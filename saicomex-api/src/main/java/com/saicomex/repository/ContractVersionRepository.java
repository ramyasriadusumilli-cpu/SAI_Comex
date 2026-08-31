package com.saicomex.repository;

import com.saicomex.entity.ContractVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ContractVersionRepository extends JpaRepository<ContractVersion, Long> {

    List<ContractVersion> findAllByContractIdOrderByVersionNumberDesc(Long contractId);

    Optional<ContractVersion> findByContractIdAndVersionNumber(Long contractId, Integer versionNumber);

    @Query("SELECT MAX(cv.versionNumber) FROM ContractVersion cv WHERE cv.contractId = :contractId")
    Integer findMaxVersionNumber(@Param("contractId") Long contractId);
}
