package com.saicomex.repository;

import com.saicomex.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByUserIdAndIsReadFalse(Long userId);

    @Modifying
    @Transactional
    @Query("""
           UPDATE Notification n SET n.isRead = TRUE, n.readAt = :readAt
           WHERE n.userId = :userId AND n.isRead = FALSE
           """)
    int markAllReadForUser(@Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
