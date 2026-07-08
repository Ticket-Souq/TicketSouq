package org.ticketsouq.sharedmodule.utils;

import java.util.UUID;

public final class UUIDUtils {

    private UUIDUtils() {}


    public static UUID parse(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID format: " + id);
        }
    }
}
