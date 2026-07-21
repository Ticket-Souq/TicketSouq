package org.ticketsouq.analyticsservice.model;

import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder @EqualsAndHashCode
public class EventRevenueByTierId implements Serializable {

    private String organizationId;
    private String eventId;
    private String tierId;
    private LocalDate day;
}
