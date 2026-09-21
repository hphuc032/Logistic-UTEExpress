package com.uteexpress.governance;

import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Testcontainers
class AuditLogIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired AuditLogService audit;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Flyway flyway;

    @BeforeEach
    void clearIsolatedFixture() { jdbc.update("DELETE FROM uteexpress.audit_logs"); }

    private AuditEntry entry(Long actorId) {
        return new AuditEntry(actorId, "CATEGORY_UPDATED", "CATEGORY", 42L,
                Map.of("active", "false"), Map.of("active", "true", "version", "1"),
                "CONFIGURATION_CHANGE");
    }

    @Test
    void migratesOnCleanDatabaseAndCanRerun() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.table_constraints "
                + "WHERE table_schema='uteexpress' AND table_name='audit_logs' "
                + "AND constraint_type='FOREIGN KEY'", Integer.class)).isEqualTo(1);
    }

    @Test
    void appendsWithVerifiedActorAndSafeSummariesInCallerTransaction() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            Long actor = jdbc.queryForObject("INSERT INTO uteexpress.users "
                    + "(email,normalized_email,username,normalized_username,password_hash,status) "
                    + "VALUES ('audit@example.test','audit@example.test','audit_actor','audit_actor',"
                    + "'test-only-hash-not-used-for-login','ACTIVE') RETURNING id", Long.class);
            Long id = audit.append(entry(actor));
            Map<String, Object> row = jdbc.queryForMap("SELECT * FROM uteexpress.audit_logs WHERE id=?", id);
            assertThat(row.get("actor_id")).isEqualTo(actor);
            assertThat(row.get("before_summary")).isEqualTo("active=false");
            assertThat(row.get("after_summary")).isEqualTo("active=true;version=1");
            assertThat(row.get("created_at")).isNotNull();
            status.setRollbackOnly();
        });
    }

    @Test
    void systemActionCanCommitWithNullActor() {
        Long id = new TransactionTemplate(transactions).execute(status -> audit.append(entry(null)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs "
                + "WHERE id=? AND actor_id IS NULL", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void rollbackRemovesAuditAlongsideBusinessChange() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
            audit.append(entry(null));
            throw new IllegalStateException("business operation failed");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs", Integer.class)).isZero();
    }

    @Test
    void refusesMissingTransaction() {
        assertThatThrownBy(() -> audit.append(entry(null)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs", Integer.class)).isZero();
    }

    @Test
    void missingActorFailsTheTransaction() {
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(
                status -> audit.append(entry(Long.MAX_VALUE))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs", Integer.class)).isZero();
    }

    @Test
    void databaseRejectsInvalidLogicalTarget() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.audit_logs "
                + "(action,target_type,target_id,before_summary,after_summary,reason) "
                + "VALUES ('UPDATED','CATEGORY',0,'','','CONFIGURATION_CHANGE')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
