package org.ticketsouq.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.userservice.model.MemberRole;
import org.ticketsouq.userservice.model.OrgMember;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.model.Organization;
import org.ticketsouq.userservice.repository.OrgMemberRepository;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private final OrgMemberRepository orgMemberRepository;

    @Transactional
    public void changeStatus(UUID orgHeadId, OrgStatus newStatus) {
        OrgMember head = orgMemberRepository.findByUserIdAndMemberRole(orgHeadId, MemberRole.HEAD)
            .orElseThrow(() -> new BusinessException("Organization HEAD not found", HttpStatus.NOT_FOUND));

        Organization org = head.getOrganization();
        org.setStatus(newStatus);
        
        log.info("Changed status of Organization {} to {}", org.getName(), newStatus);
    }
}
