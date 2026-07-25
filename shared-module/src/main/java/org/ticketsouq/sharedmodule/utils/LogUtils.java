package org.ticketsouq.sharedmodule.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

@Slf4j
public final class LogUtils {

    private LogUtils() {}

    public static void logEventPublished(String service ,String topic){
        log.info("Service [{}] Publishing [{}] event ", service, topic);
    }

    public static void logEventPublishingFailed(String service ,String topic){
        log.error("Service [{}] failed to Publish [{}] event ", service, topic);
    }

    public static void logEventConsumed(String service ,String topic){
        log.info("Service [{}] Received [{}] event", service , topic);
    }

}
