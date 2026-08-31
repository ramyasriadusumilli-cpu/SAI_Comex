package com.saicomex.repository;

import com.saicomex.entity.Buyer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Every query filters {@code deletedAt IS NULL} explicitly — see the note on
 * {@link com.saicomex.common.SoftDeletableEntity}.
 */
public interface BuyerRepository extends JpaRepository<Buyer, Long> {

    List<Buyer> findAllByDeletedAtIsNullOrderByNameAsc();

    Optional<Buyer> findByIdAndDeletedAtIsNull(Long id);
}
