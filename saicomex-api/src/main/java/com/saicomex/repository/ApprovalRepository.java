package com.saicomex.repository;

import com.saicomex.entity.Approval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findAllByEntityTypeAndEntityIdOrderByActedAtDesc(String entityType, Long entityId);

    Page<Approval> findAllByEntityTypeOrderByActedAtDesc(String entityType, Pageable pageable);
}
