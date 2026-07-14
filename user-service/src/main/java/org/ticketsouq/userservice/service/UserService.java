package org.ticketsouq.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.userservice.mapper.UserMapper;
import org.ticketsouq.userservice.model.MemberRole;
import org.ticketsouq.userservice.model.OrgMember;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.model.Organization;
import org.ticketsouq.userservice.model.User;
import org.ticketsouq.userservice.repository.OrgMemberRepository;
import org.ticketsouq.userservice.repository.OrganizationRepository;
import org.ticketsouq.userservice.repository.UserRepository;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final OrgMemberRepository orgMemberRepository;
    private final UserMapper userMapper;

    @Transactional
    public void register(CreateUserRequest req) {
        // register as (ORG_HEAD)
        if (req.OrganizationName() != null && !req.OrganizationName().isBlank()) {
            if (organizationRepository.existsByName(req.OrganizationName())) {
                throw new BusinessException("Organization name already taken", HttpStatus.CONFLICT);
            }

            Organization org = Organization.builder()
                .name(req.OrganizationName())
                .status(OrgStatus.PENDING)
                .build();
            organizationRepository.save(org);

            User user = User.builder()
                .id(req.userId())
                .name(req.name())
                .email(req.email())
                .build();
            userRepository.save(user);

            OrgMember head = OrgMember.builder()
                .userId(user.getId())
                .user(user)
                .organization(org)
                .memberRole(MemberRole.HEAD)
                .build();
            orgMemberRepository.save(head);

            log.info("Registered new ORG_HEAD {} for Organization {}", req.email(), org.getName());
            return;
        }

        // register as a Customer
        User user = User.builder()
            .id(req.userId())
            .name(req.name())
            .email(req.email())
            .build();
        userRepository.save(user);
        log.info("Registered new CUSTOMER {}", req.email());
    }

    @Transactional(readOnly = true)
    public boolean isBelongToBannedOrg(UUID userId) {
        return orgMemberRepository.findById(userId)
            .map(member -> member.getOrganization().getStatus() == OrgStatus.BANNED)
            .orElse(false);
    }

    @Transactional
    public void generateMembers(GenerateMembersRequest request) {
        // find org of Org_Head
        OrgMember head = orgMemberRepository.findByUserIdAndMemberRole(request.orgHeadUserId(), MemberRole.HEAD)
            .orElseThrow(() -> new BusinessException("Valid ORG_HEAD not found", HttpStatus.FORBIDDEN));

        Organization org = head.getOrganization();

        List<GenerateMembersRequest.MemberToCreate> membersToCreate = request.members();
        for (GenerateMembersRequest.MemberToCreate m : membersToCreate) {
            User user = User.builder()
                .id(m.userId())
                .email(m.email())
                .name(m.email().split("@")[0]) // take email prefix as init userName
                .build();
            userRepository.save(user);

            // Role Mapping
            String rawRole = m.role().replace("ORG_", "");
            MemberRole roleEnum = MemberRole.valueOf(rawRole);

            OrgMember member = OrgMember.builder()
                .userId(user.getId())
                .user(user)
                .organization(org)
                .memberRole(roleEnum)
                .invitedBy(head.getUserId())
                .build();
            orgMemberRepository.save(member);
        }
        log.info("Generated {} members for organization {}", membersToCreate.size(), org.getName());
    }

    @Transactional(readOnly = true)
    public String getOrganizationNameByUserId(UUID userId) {
        return orgMemberRepository.findById(userId)
            .map(member -> member.getOrganization().getName())
            .orElse(null);
    }
}
