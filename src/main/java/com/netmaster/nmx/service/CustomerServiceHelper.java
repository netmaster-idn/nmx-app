package com.netmaster.nmx.service;

import com.netmaster.nmx.model.CustomerServiceEntity;
import com.netmaster.nmx.repository.CustomerServiceEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerServiceHelper {

    private final CustomerServiceEntityRepository customerServiceEntityRepository;

    public Optional<CustomerServiceEntity> getServiceByCustomerId(Long customerId) {
        List<CustomerServiceEntity> services = customerServiceEntityRepository.findByCustomerId(customerId);
        return services.isEmpty() ? Optional.empty() : Optional.of(services.get(0));
    }
}

