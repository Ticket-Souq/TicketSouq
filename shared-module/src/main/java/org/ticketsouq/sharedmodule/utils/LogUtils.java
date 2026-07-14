package org.ticketsouq.sharedmodule.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public final class LogUtils {

    private LogUtils() {}

    public static void logEventPublished(String Service ,String topic){
        log.info("Service {} Publishing {} event ", Service, topic);
    }

    public static void logEventConsumed(String Service ,String topic){
        log.info("Service {} Received {} event", Service , topic);
    }

}
