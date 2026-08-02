package org.ticketsouq.outbox.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.ticketsouq.outbox.relay.OutboxProperties;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxJpaConfiguration {

    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    @Value("${spring.jpa.properties.hibernate.format_sql:false}")
    private boolean formatSql;

    @Bean
    HibernatePropertiesCustomizer outboxSqlStatementInspectorCustomizer() {
        return properties -> {
            properties.put("hibernate.show_sql", false);
            properties.put("hibernate.session_factory.statement_inspector",
                    new OutboxSqlStatementInspector(showSql, formatSql));
        };
    }
}
