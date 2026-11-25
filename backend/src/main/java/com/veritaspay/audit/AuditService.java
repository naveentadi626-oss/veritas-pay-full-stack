package com.veritaspay.audit;

import com.veritaspay.common.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(UUID actorId, String actorEmail, String action,
                       String resourceType, String resourceId, String details) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        auditLogRepository.save(new AuditLog(
                actorId, actorEmail, action, resourceType, resourceId, correlationId, details));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> listAuditLogs(Pageable pageable) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
