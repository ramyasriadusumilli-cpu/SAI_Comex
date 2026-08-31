package com.saicomex.repository;

import com.saicomex.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    List<User> findAllByDeletedAtIsNullOrderByLastNameAsc();

    Optional<User> findByResetToken(String resetToken);

    long countByStatusAndDeletedAtIsNull(String status);

    @Query("""
           SELECT u FROM User u
           WHERE u.deletedAt IS NULL
             AND (CAST(:status AS string) IS NULL OR u.status = :status)
             AND (CAST(:roleId AS long) IS NULL OR u.roleId = :roleId)
             AND (CAST(:search AS string) IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                                  OR LOWER(u.email)     LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
           """)
    Page<User> search(@Param("status") String status,
                      @Param("roleId") Long roleId,
                      @Param("search") String search,
                      Pageable pageable);
}
