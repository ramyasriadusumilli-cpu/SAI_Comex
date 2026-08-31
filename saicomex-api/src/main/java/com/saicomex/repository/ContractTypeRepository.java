package com.saicomex.repository;

import com.saicomex.entity.ContractType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContractTypeRepository extends JpaRepository<ContractType, Long> {

    List<ContractType> findAllByIsActiveTrueOrderByDisplayOrderAsc();

    Optional<ContractType> findByCode(String code);
}
