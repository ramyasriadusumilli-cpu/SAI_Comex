package com.saicomex.repository;

import com.saicomex.entity.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CurrencyRepository extends JpaRepository<Currency, String> {

    List<Currency> findAllByIsActiveTrueOrderByDisplayOrderAsc();
}
