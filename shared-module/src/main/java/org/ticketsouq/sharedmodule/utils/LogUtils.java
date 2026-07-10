package org.ticketsouq.sharedmodule.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public final class LogUtils {

    private LogUtils() {}

    public static void log(String topic, UUID userId){
        log.info("Publishing {} event for userId={}", topic, userId);
    }

}
