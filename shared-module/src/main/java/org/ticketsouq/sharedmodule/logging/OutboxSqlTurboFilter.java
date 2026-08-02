package org.ticketsouq.sharedmodule.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

public class OutboxSqlTurboFilter extends TurboFilter {

    private static final String SQL_LOGGER = "org.hibernate.SQL";
    private static final String OUTBOX_TABLE = "ticket_souq_outbox";

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format,
                              Object[] params, Throwable throwable) {
        if (logger != null && SQL_LOGGER.equals(logger.getName())
                && format != null && format.toLowerCase().contains(OUTBOX_TABLE)) {
            return FilterReply.DENY;
        }
        return FilterReply.NEUTRAL;
    }
}
