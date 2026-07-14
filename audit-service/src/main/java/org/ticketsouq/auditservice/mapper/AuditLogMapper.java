package org.ticketsouq.auditservice.mapper;

import org.mapstruct.Mapper;
import org.ticketsouq.auditservice.dto.AuditLogResponse;
import org.ticketsouq.auditservice.entity.AuditLog;

@Mapper(componentModel = "spring")
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog entity);
}
