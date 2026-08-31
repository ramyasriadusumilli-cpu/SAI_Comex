package com.saicomex.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Paged envelope returned by every list endpoint.
 *
 * <p>Spring's own {@code Page} serialises its internal {@code Pageable} and
 * {@code Sort} structure, which is unstable across Spring versions and leaks
 * implementation detail to the client. This is the stable contract instead.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }

    public static <T> PageResponse<T> of(List<T> all) {
        return new PageResponse<>(all, 0, all.size(), all.size(), 1, true, true);
    }
}
