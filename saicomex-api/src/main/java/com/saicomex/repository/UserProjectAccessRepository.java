package com.saicomex.repository;

import com.saicomex.entity.UserProjectAccess;
import com.saicomex.entity.UserProjectAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserProjectAccessRepository extends JpaRepository<UserProjectAccess, UserProjectAccessId> {

    List<UserProjectAccess> findAllByIdUserId(Long userId);

    @Modifying
    @Transactional
    void deleteAllByIdUserId(Long userId);

    @Query("SELECT a.id.projectId FROM UserProjectAccess a WHERE a.id.userId = :userId")
    List<Long> findProjectIdsByUserId(@Param("userId") Long userId);
}
