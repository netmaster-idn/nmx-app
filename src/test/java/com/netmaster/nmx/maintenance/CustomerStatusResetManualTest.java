package com.netmaster.nmx.maintenance;

import com.netmaster.nmx.repository.CustomerRepository;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Commit;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Commit
class CustomerStatusResetManualTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private CustomerServiceEntityRepository customerServiceEntityRepository;

    @Test
    void resetAllCustomersAndServicesToPending() {
        int updatedCustomers = customerRepository.resetAllCustomerStatusesToPending();
        int updatedServices = customerServiceEntityRepository.resetAllServiceStatusesToPending();

        assertThat(updatedCustomers).isGreaterThanOrEqualTo(0);
        assertThat(updatedServices).isGreaterThanOrEqualTo(0);
        assertThat(customerRepository.countByStatus("active")).isZero();
        assertThat(customerRepository.countByStatus("suspended")).isZero();
        assertThat(customerServiceEntityRepository.findByStatusOrderByCreatedAtDesc("active")).isEmpty();
        assertThat(customerServiceEntityRepository.findByStatusOrderByCreatedAtDesc("suspended")).isEmpty();
    }
}

