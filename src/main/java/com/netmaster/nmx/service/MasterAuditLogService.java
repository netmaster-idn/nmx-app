package com.netmaster.nmx.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netmaster.nmx.master.model.MasterAuditLog;
import com.netmaster.nmx.master.model.SuperadminActivityLog;
import com.netmaster.nmx.master.repository.MasterAuditLogRepository;
import com.netmaster.nmx.master.repository.SuperadminActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class MasterAuditLogService {

    private final MasterAuditLogRepository auditLogRepository;
    private final SuperadminActivityLogRepository superadminActivityLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional("masterTransactionManager")
    public void record(String actorType,
                       Long actorId,
                       String action,
                       Long tenantId,
                       String targetType,
                       String targetId,
                       Map<String, Object> metadata,
                       String requestIp) {
        MasterAuditLog log = new MasterAuditLog();
        log.setActorType(actorType);
        log.setActorId(actorId);
        log.setAction(action);
        log.setTenantId(tenantId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setMetadataJson(writeJson(metadata));
        log.setRequestIp(requestIp);
        auditLogRepository.save(log);

        SuperadminActivityLog activityLog = new SuperadminActivityLog();
        activityLog.setTenantId(tenantId);
        activityLog.setActivity(buildActivityMessage(action, metadata));
        superadminActivityLogRepository.save(activityLog);
    }

    private String writeJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":true}";
        }
    }

    private String buildActivityMessage(String action, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return action;
        }

        Object companyName = metadata.get("companyName");
        Object tenantSlug = metadata.get("tenantSlug");
        Object reason = metadata.get("reason");

        if (companyName != null) {
            return action + " • " + companyName;
        }
        if (tenantSlug != null) {
            return action + " • " + tenantSlug;
        }
        if (reason != null) {
            return action + " • " + reason;
        }
        return action;
    }
}
