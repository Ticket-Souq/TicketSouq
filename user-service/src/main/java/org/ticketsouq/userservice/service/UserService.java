package org.ticketsouq.userservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.ticketsouq.sharedmodule.ApiGateway.dto.CreateUserRequest;
import org.ticketsouq.sharedmodule.ApiGateway.dto.GenerateMembersRequest;
import org.ticketsouq.sharedmodule.GeneralExceptions.BusinessException;
import org.ticketsouq.userservice.dto.OrgMemberResponse;
import org.ticketsouq.userservice.dto.UserProfileResponse;
import org.ticketsouq.userservice.mapper.UserMapper;
import org.ticketsouq.userservice.model.MemberRole;
import org.ticketsouq.userservice.model.OrgMember;
import org.ticketsouq.userservice.model.OrgStatus;
import org.ticketsouq.userservice.model.Organization;
import org.ticketsouq.userservice.model.User;
import org.ticketsouq.sharedmodule.UserService.dto.UserEmail;
import org.ticketsouq.userservice.repository.OrgMemberRepository;
import org.ticketsouq.userservice.repository.OrganizationRepository;
import org.ticketsouq.userservice.repository.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
        if (req.organizationName() != null && !req.organizationName().isBlank()) {
            if (organizationRepository.existsByName(req.organizationName())) {
                throw new BusinessException("Organization name already taken", HttpStatus.CONFLICT);
            }

            Organization org = Organization.builder()
                .name(req.organizationName())
                .status(OrgStatus.PENDING)
                .build();
            organizationRepository.save(org);

            User user = User.builder()
                .id(req.userId())
                .name(req.name())
                .email(req.email())
                .build();
            user = userRepository.save(user);

            OrgMember head = OrgMember.builder()
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
            user = userRepository.save(user);

            // Role Mapping
            String rawRole = m.role().replace("ORG_", "").toUpperCase();
            MemberRole roleEnum = MemberRole.valueOf(rawRole);

            OrgMember member = OrgMember.builder()
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
        return orgMemberRepository.findOrganizationNameByUserId(userId).orElse(null);
    }

    @Transactional(readOnly = true)
    public String getOrgHeadEmailByOrgName(String organizationName) {
        OrgMember head = orgMemberRepository
            .findByOrganization_NameAndMemberRole(organizationName, MemberRole.HEAD)
            .orElseThrow(() -> new BusinessException(
                "Organization not found or has no HEAD", HttpStatus.NOT_FOUND));
        return head.getUser().getEmail();
    }

    @Transactional(readOnly = true)
    public Map<UUID, String> getUserNames(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        return userRepository.findNamesByIds(userIds).stream()
            .collect(Collectors.toMap(
                row -> (UUID) row[0],
                row -> (String) row[1]
            ));
    }

    @Transactional(readOnly = true)
    public List<OrgMemberResponse> getOrgMembersByHead(UUID headUserId) {
        OrgMember head = orgMemberRepository.findByUserIdAndMemberRole(headUserId, MemberRole.HEAD)
            .orElseThrow(() -> new BusinessException("Organization head not found", HttpStatus.NOT_FOUND));
        return orgMemberRepository.findByOrganization_Id(head.getOrganization().getId()).stream()
            .map(userMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<UserEmail> getUsersEmails(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        return userRepository.findMemberSummariesByIds(ids);
    }


    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException("User not found", HttpStatus.NOT_FOUND));

        return orgMemberRepository.findById(userId)
            .map(member -> new UserProfileResponse(user.getName(), user.getEmail(), member.getOrganization().getName()))
            .orElse(new UserProfileResponse(user.getName(), user.getEmail(), null));
    }
}
