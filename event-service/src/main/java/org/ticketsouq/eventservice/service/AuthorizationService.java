package org.ticketsouq.eventservice.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.ticketsouq.eventservice.Client.UserServiceClient;
import org.ticketsouq.sharedmodule.GeneralExceptions.BadRequestException;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizationService {

    private final UserServiceClient userServiceClient;

    public boolean validateOrganizer(UUID organizationId, UUID userId) {

//        boolean allowed = userServiceClient.isOrganizer(
//            organizationId,
//            userId
//        );
//
//        if (!allowed) {
//            throw new BadRequestException(
//                "User is not allowed to perform this action."
//            );
//        }
        return true;
    }

    public boolean validateCanManageEvent(UUID organizationId, UUID userId) {

//        boolean allowed = userServiceClient.canManageEvent(
//            organizationId,
//            userId
//        );
//
//        if (!allowed) {
//            throw new BadRequestException(
//                "User is not allowed to perform this action."
//            );
//        }
        return true;
    }
}
