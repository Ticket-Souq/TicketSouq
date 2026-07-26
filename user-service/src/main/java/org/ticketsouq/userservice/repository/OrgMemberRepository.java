package org.ticketsouq.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.ticketsouq.userservice.dto.OrganizationWithHeadResponse;
import org.ticketsouq.userservice.model.MemberRole;
import org.ticketsouq.userservice.model.OrgMember;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrgMemberRepository extends JpaRepository<OrgMember, UUID> {

    List<OrgMember> findByOrganization_Id(UUID orgId);

    Optional<OrgMember> findByUserIdAndMemberRole(UUID userId, MemberRole role);

    boolean existsByOrganization_IdAndMemberRole(UUID orgId, MemberRole role);

    @Query("SELECT om.organization.name FROM OrgMember om WHERE om.userId = :userId")
    Optional<String> findOrganizationNameByUserId(@Param("userId") UUID userId);

    Optional<OrgMember> findByOrganization_NameAndMemberRole(String organizationName, MemberRole role);

    Optional<OrgMember> findByUserId(UUID userId);

    @Query("SELECT new org.ticketsouq.userservice.dto.OrganizationWithHeadResponse(" +
           "om.organization.id, om.organization.name, om.user.email) " +
           "FROM OrgMember om " +
           "WHERE om.memberRole = org.ticketsouq.userservice.model.MemberRole.HEAD")
    List<OrganizationWithHeadResponse> findAllOrganizationsWithHeadEmail();
}
