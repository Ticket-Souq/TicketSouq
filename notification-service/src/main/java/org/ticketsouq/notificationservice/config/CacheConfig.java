package org.ticketsouq.notificationservice.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30));
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {

        CaffeineCacheManager cacheManager = new CaffeineCacheManager("events");
        cacheManager.setCaffeine(caffeine);

        return cacheManager;
    }
}
