package com.saicomex.service;

import com.saicomex.exception.BusinessRuleException;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * SRS §35 — a thin wrapper over MinIO object storage.
 *
 * <p>The client is built lazily on first use, not as a {@code @Bean}: a local
 * developer without MinIO running should still be able to boot the API and
 * use every module except documents. Every method throws a readable
 * {@link BusinessRuleException} when {@code app.storage.enabled=false} rather
 * than trying to connect.
 *
 * <p><b>{@code MINIO_ENDPOINT} must be the public HTTPS URL the operator's own
 * browser can reach</b> (e.g. {@code https://files.saicomex.com}), never a
 * docker-internal hostname such as {@code http://minio:9000}. A presigned URL
 * is signed against this endpoint and handed straight to the browser — a
 * container-only hostname would resolve on the server and fail for everyone
 * who tries to actually open the link.
 */
@Service
@Slf4j
public class StorageService {

    @Value("${app.storage.endpoint}")
    private String endpoint;

    @Value("${app.storage.access-key}")
    private String accessKey;

    @Value("${app.storage.secret-key}")
    private String secretKey;

    @Value("${app.storage.bucket}")
    private String bucket;

    @Value("${app.storage.presign-expiry-minutes}")
    private int presignExpiryMinutes;

    @Value("${app.storage.enabled}")
    private boolean enabled;

    private volatile MinioClient client;

    public String upload(MultipartFile file, String objectKey) {
        try (InputStream in = file.getInputStream()) {
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());
            return objectKey;
        } catch (Exception e) {
            log.error("MinIO upload failed for {}", objectKey, e);
            throw new BusinessRuleException("Failed to store the uploaded file: " + e.getMessage());
        }
    }

    public String presignedUrl(String objectKey) {
        try {
            return client().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(presignExpiryMinutes, TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            log.error("MinIO presign failed for {}", objectKey, e);
            throw new BusinessRuleException("Failed to generate a download link: " + e.getMessage());
        }
    }

    public void delete(String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            log.error("MinIO delete failed for {}", objectKey, e);
            throw new BusinessRuleException("Failed to delete the stored file: " + e.getMessage());
        }
    }

    private MinioClient client() {
        if (!enabled) {
            throw new BusinessRuleException(
                    "Document storage is disabled in this environment (app.storage.enabled=false)");
        }
        MinioClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
                    ensureBucket(c);
                    client = c;
                }
            }
        }
        return c;
    }

    private void ensureBucket(MinioClient c) {
        try {
            boolean exists = c.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                c.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception e) {
            log.error("MinIO bucket check/create failed for {}", bucket, e);
            throw new BusinessRuleException("Could not reach document storage: " + e.getMessage());
        }
    }
}
