package com.saicomex.config;

import com.saicomex.entity.Company;
import com.saicomex.repository.CompanyRepository;
import com.saicomex.repository.ContractRepository;
import com.saicomex.repository.CurrencyRepository;
import com.saicomex.repository.RoleRepository;
import com.saicomex.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs once at boot and refuses to start on three specific configuration
 * gaps. Each of the three would otherwise surface much later as a confusing
 * 500 deep in a request — a settlement that can't find a reporting currency,
 * an audit stamp with no user behind it, a login with no role to grant — so
 * they are checked here, once, with a message that says what is missing
 * instead of a stack trace that says where it broke.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0)
public class StartupValidator implements ApplicationRunner {

    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final ContractRepository contractRepository;

    @Value("${app.reporting-currency}")
    private String reportingCurrency;

    @Value("${app.storage.enabled}")
    private boolean storageEnabled;

    @Value("${app.storage.bucket:}")
    private String storageBucket;

    @Override
    public void run(ApplicationArguments args) {
        log.info("=================================================================");
        log.info(" SAIComex Mining Platform — startup checks");
        log.info("=================================================================");

        // Hard check 1: without a company row, every service that stamps
        // company_id on a new record (production, expenses, sales, ...)
        // throws "the database has not been seeded" on the FIRST write a
        // user attempts, not at boot where the cause is obvious.
        Company company = companyRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No company record exists. Run the V1 migration's seed data or insert one manually "
                        + "before starting the application — every business table stamps company_id."));
        log.info(" Company: {} ({})", company.getName(), company.getCode());

        // Hard check 2: with no role, no user can be assigned one and
        // authentication fails for everybody; with no ACTIVE user, every
        // login fails, which is otherwise a mystifying "account disabled"
        // on day one for whoever tries to sign in first.
        long roleCount = roleRepository.count();
        if (roleCount == 0) {
            throw new IllegalStateException(
                    "No roles exist. The V7 reference-data migration seeds the standard role set — "
                    + "confirm it ran before starting the application.");
        }
        long activeUsers = userRepository.countByStatusAndDeletedAtIsNull("ACTIVE");
        if (activeUsers == 0) {
            throw new IllegalStateException(
                    "No ACTIVE user exists. There is no account that can sign in — create one directly in the "
                    + "database before starting the application.");
        }
        log.info(" Roles: {}   Active users: {}", roleCount, activeUsers);

        // Hard check 3: every stored base_amount is expressed in this
        // currency (see the comment on app.reporting-currency in
        // application.yml). If the code itself isn't in the currencies
        // table, every FX conversion and every settlement waterfall fails
        // the first time it tries to look the currency up, not at boot.
        boolean reportingCurrencyExists = currencyRepository.findById(reportingCurrency).isPresent();
        if (!reportingCurrencyExists) {
            throw new IllegalStateException(
                    "Configured reporting currency '" + reportingCurrency + "' (app.reporting-currency) does not "
                    + "exist in the currencies table. Add it, or point the configuration at a currency that exists.");
        }
        log.info(" Reporting currency: {}", reportingCurrency);

        // Soft check: storage enabled with no bucket configured means every
        // document upload will fail at the MinIO client, not here — worth a
        // loud warning, not worth blocking boot over (documents are not on
        // the critical path for production, expenses, sales or payments).
        if (storageEnabled && (storageBucket == null || storageBucket.isBlank())) {
            log.warn(" app.storage.enabled is true but app.storage.bucket is not set — "
                    + "document uploads will fail until MINIO_BUCKET is configured.");
        }

        // Soft check: no ACTIVE contract means nothing can be settled yet.
        // Legitimate on a brand-new install, so a warning, not a failure.
        long activeContracts = contractRepository.countByStatusAndDeletedAtIsNull("ACTIVE");
        if (activeContracts == 0) {
            log.warn(" No ACTIVE contract exists yet — no shaft can be settled until at least one is activated.");
        } else {
            log.info(" Active contracts: {}", activeContracts);
        }

        log.info("=================================================================");
        log.info(" Startup checks passed.");
        log.info("=================================================================");
    }
}
