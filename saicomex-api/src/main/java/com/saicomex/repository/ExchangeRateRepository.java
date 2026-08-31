package com.saicomex.repository;

import com.saicomex.entity.ExchangeRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /**
     * Most recent rate for a currency pair on or before {@code onDate}.
     *
     * <p>Expressed as a derived {@code findFirstBy…} query rather than a
     * {@code @Query} with a {@code Pageable}: Spring Data rejects a
     * {@code Pageable} parameter on a method returning {@code Optional}, and it
     * rejects it at context startup, not at compile time.
     */
    Optional<ExchangeRate> findFirstByFromCurrencyAndToCurrencyAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            String fromCurrency, String toCurrency, LocalDate onDate);

    List<ExchangeRate> findAllByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(
            String fromCurrency, String toCurrency);
}
