package com.saicomex.repository;

import com.saicomex.entity.UserShaftAccess;
import com.saicomex.entity.UserShaftAccessId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserShaftAccessRepository extends JpaRepository<UserShaftAccess, UserShaftAccessId> {

    List<UserShaftAccess> findAllByIdUserId(Long userId);

    @Modifying
    @Transactional
    void deleteAllByIdUserId(Long userId);

    @Query("SELECT a.id.shaftId FROM UserShaftAccess a WHERE a.id.userId = :userId")
    List<Long> findShaftIdsByUserId(@Param("userId") Long userId);
}
