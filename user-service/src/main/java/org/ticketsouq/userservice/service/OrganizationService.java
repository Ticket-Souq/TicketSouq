package org.ticketsouq.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.AuditService.events.AuditEvent;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.userservice.model.MemberRole;
import org.ticketsouq.userservice.model.OrgMember;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.model.Organization;
import org.ticketsouq.userservice.dto.OrganizationWithHeadResponse;
import org.ticketsouq.userservice.repository.OrgMemberRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrgMemberRepository orgMemberRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Transactional
    public void changeStatus(UUID orgHeadId, OrgStatus newStatus, UUID adminId) {
        OrgMember head = orgMemberRepository.findByUserIdAndMemberRole(orgHeadId, MemberRole.HEAD)
            .orElseThrow(() -> new BusinessException("Organization HEAD not found", HttpStatus.NOT_FOUND));

        Organization org = head.getOrganization();
        org.setStatus(newStatus);

        applicationEventPublisher.publishEvent(new AuditEvent(
            "Change Organization [" + org.getName() + "] Status to " + newStatus.name(),
            adminId, "Admin Decision", Instant.now()
        ));

        log.info("Admin {} changed status of Organization {} to {}", adminId, org.getName(), newStatus);
    }

    @Transactional(readOnly = true)
    public List<OrganizationWithHeadResponse> getAllOrganizations() {
        return orgMemberRepository.findAllOrganizationsWithHeadEmail();
    }
}
