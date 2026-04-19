package com.netmaster.nmx.service;

import com.netmaster.nmx.model.ActivityLogEntry;
import com.netmaster.nmx.model.User;
import com.netmaster.nmx.repository.ActivityLogEntryRepository;
import com.netmaster.nmx.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingAuditTrailService {

    private final ActivityLogEntryRepository activityLogEntryRepository;
    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String description) {
        try {
            ActivityLogEntry entry = new ActivityLogEntry();
            entry.setUserId(resolveCurrentUserId());
            entry.setAction(action);
            entry.setDescription(description);
            activityLogEntryRepository.save(entry);
        } catch (Exception ex) {
            log.warn("Failed to save billing activity log: {}", ex.getMessage());
        }
    }

    private Long resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        return userRepository.findByUsername(authentication.getName())
                .map(User::getId)
                .orElse(null);
    }
}
