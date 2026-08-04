package org.ticketsouq.ticketservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TicketMetrics {

    private final MeterRegistry registry;
    private final Counter ticketsSold;
    private final Counter ticketsConsumed;
    private final Counter ticketsCancelled;

    public TicketMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.ticketsSold = Counter.builder("tickets.sold")
            .description("Total tickets sold")
            .register(registry);
        this.ticketsConsumed = Counter.builder("tickets.consumed")
            .description("Total tickets consumed at venue")
            .register(registry);
        this.ticketsCancelled = Counter.builder("tickets.cancelled")
            .description("Total tickets cancelled")
            .register(registry);
    }

    public void recordTicketSold() {
        ticketsSold.increment();
    }

    public void recordTicketSold(String category) {
        ticketsSold.increment();
        Counter.builder("tickets.sold")
            .description("Total tickets sold by category")
            .tag("category", category)
            .register(registry)
            .increment();
    }

    public void recordTicketConsumed() {
        ticketsConsumed.increment();
    }

    public void recordTicketCancelled() {
        ticketsCancelled.increment();
    }
}
