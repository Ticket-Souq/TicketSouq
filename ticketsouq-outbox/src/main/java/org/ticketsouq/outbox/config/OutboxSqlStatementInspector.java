package org.ticketsouq.outbox.config;

import org.hibernate.engine.jdbc.spi.SqlStatementLogger;
import org.hibernate.resource.jdbc.spi.StatementInspector;

public class OutboxSqlStatementInspector implements StatementInspector {

    private static final String OUTBOX_TABLE = "ticket_souq_outbox";

    private final boolean showSql;
    private final SqlStatementLogger sqlStatementLogger;

    public OutboxSqlStatementInspector(boolean showSql, boolean formatSql) {
        this.showSql = showSql;
        this.sqlStatementLogger = new SqlStatementLogger(true, formatSql, false);
    }

    @Override
    public String inspect(String sql) {
        if (showSql && sql != null && !sql.toLowerCase().contains(OUTBOX_TABLE)) {
            sqlStatementLogger.logStatement(sql);
        }
        return sql;
    }
}
