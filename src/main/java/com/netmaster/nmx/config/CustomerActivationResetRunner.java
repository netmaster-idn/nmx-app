package com.netmaster.nmx.config;

import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "nmx.customer.reset-all-pending", havingValue = "true")
@Slf4j
public class CustomerActivationResetRunner implements ApplicationRunner {

    private final CustomerRepository customerRepository;
    private final CustomerServiceEntityRepository customerServiceEntityRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int updatedCustomers = customerRepository.resetAllCustomerStatusesToPending();
        int updatedServices = customerServiceEntityRepository.resetAllServiceStatusesToPending();

        log.warn("RESET STATUS PELANGGAN: {} customer diubah ke pending.", updatedCustomers);
        log.warn("RESET STATUS LAYANAN: {} customer service diubah ke pending dan activation_date dikosongkan.", updatedServices);
    }
}
