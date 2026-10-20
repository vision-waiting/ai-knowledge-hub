package com.example.knowledge.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

/** Additive upgrades preserve existing installations and their documents. */
@Component
@DependsOn("productionConfiguration")
public class SchemaMigration implements InitializingBean {
    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    public SchemaMigration(DataSource dataSource, JdbcTemplate jdbc) { this.dataSource = dataSource; this.jdbc = jdbc; }
    @Override public void afterPropertiesSet() throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        addColumn("sys_user", "token_version", "INT NOT NULL DEFAULT 0");
        addColumn("chat_message", "mode", "VARCHAR(32)");
        addColumn("chat_message", "model", "VARCHAR(255)");
        addColumn("chat_message", "citations_json", "LONGTEXT");
        addColumn("chat_message", "feedback", "VARCHAR(20)");
        addColumn("chat_message", "feedback_reason", "VARCHAR(200)");
        addColumn("chat_message", "feedback_status", "VARCHAR(20)");
        addColumn("chat_message", "retrieval_json", "LONGTEXT");
        addColumn("document", "summary_mode", "VARCHAR(32)");
        addColumn("document", "version_root_id", "BIGINT");
        addColumn("document", "previous_version_id", "BIGINT");
        addColumn("document", "version_number", "INT NOT NULL DEFAULT 1");
        addColumn("document", "deleted_at", "TIMESTAMP");
        addColumn("document", "deleted_status", "VARCHAR(20)");
        addColumn("knowledge_base", "shared", "BOOLEAN NOT NULL DEFAULT FALSE");
        addColumn("knowledge_content", "version", "INT NOT NULL DEFAULT 1");
    }
    private void addColumn(String table, String column, String definition) throws Exception {
        try (var connection = dataSource.getConnection(); var columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (!columns.next()) jdbc.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }
}
