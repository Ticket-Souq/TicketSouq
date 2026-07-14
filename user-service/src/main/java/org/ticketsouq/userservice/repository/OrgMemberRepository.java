package org.ticketsouq.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
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
}
