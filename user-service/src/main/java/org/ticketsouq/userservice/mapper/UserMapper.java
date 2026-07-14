package org.ticketsouq.userservice.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.ticketsouq.userservice.dto.OrgMemberResponse;
import org.ticketsouq.userservice.dto.OrganizationResponse;
import org.ticketsouq.userservice.dto.UserProfileResponse;
import org.ticketsouq.userservice.model.OrgMember;
import org.ticketsouq.userservice.model.Organization;
import org.ticketsouq.userservice.model.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "organizationName", ignore = true)
    @Mapping(target = "memberRole", ignore = true)
    UserProfileResponse toResponse(User user);

    OrganizationResponse toResponse(Organization organization);

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "name", source = "user.name")
    @Mapping(target = "email", source = "user.email")
    @Mapping(target = "orgId", source = "organization.id")
    @Mapping(target = "organizationName", source = "organization.name")
    OrgMemberResponse toResponse(OrgMember orgMember);
}
