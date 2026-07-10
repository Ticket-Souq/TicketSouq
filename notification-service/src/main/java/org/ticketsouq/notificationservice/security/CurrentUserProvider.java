package org.ticketsouq.notificationservice.security;

import java.util.UUID;

public interface CurrentUserProvider {

    UUID getCurrentUserId();

}
