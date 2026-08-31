package com.saicomex.repository;

import com.saicomex.entity.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByCodeIgnoreCase(String code);
}
