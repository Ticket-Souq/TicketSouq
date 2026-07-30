package org.ticketsouq.outbox.relay;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties("app.outbox")
public class OutboxProperties {
    private long pollInterval = 2000;
    private int maxRetries = 5;
    private int staleMinutes = 5;
}
