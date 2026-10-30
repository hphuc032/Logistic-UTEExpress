package com.uteexpress.governance;

import com.uteexpress.common.exception.*;
import com.uteexpress.governance.dto.*;
import com.uteexpress.governance.service.*;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.validation.ConstraintViolationException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @Testcontainers
class CategoryIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired CategoryService categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    Long actor;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM uteexpress.audit_logs");
        jdbc.update("DELETE FROM uteexpress.categories");
        actor = jdbc.queryForObject("INSERT INTO uteexpress.users "
                + "(email,normalized_email,username,normalized_username,password_hash,status) "
                + "VALUES ('cat' || nextval('uteexpress.users_id_seq') || '@example.test', "
                + "'cat' || currval('uteexpress.users_id_seq') || '@example.test', "
                + "'cat' || currval('uteexpress.users_id_seq'), 'cat' || currval('uteexpress.users_id_seq'), "
                + "'not-a-login-password','ACTIVE') RETURNING id", Long.class);
        authenticate(actor,"ADMIN");
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    void authenticate(Long id, String role) {
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + role));
        var principal = new UteExpressPrincipal(id,"category-actor",null,0,authorities,true);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal,null,authorities));
    }
    CategoryView create() { return categories.create(new CategoryRequest("Sách", "sach",null)); }

    @Test void createUpdateDisableEnablePersistAuditAndPreserveId() {
        CategoryView first = create();
        assertThat(first.version()).isZero();
        authenticate(actor,"MANAGER");
        CategoryView changed = categories.update(first.id(),new CategoryRequest("Sách mới","sach-moi",first.version()));
        assertThat(changed.version()).isEqualTo(1L);
        categories.setActive(first.id(),changed.version(),false);
        CategoryView hidden = categories.get(first.id());
        assertThat(hidden.active()).isFalse();
        categories.setActive(first.id(),hidden.version(),true);
        assertThat(categories.get(first.id()).active()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE actor_id=? AND target_id=?",
                Integer.class,actor,first.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.categories",Integer.class)).isEqualTo(1);
    }
    @Test void duplicateSlugRollsBackCategoryAndAudit() {
        create();
        assertThatThrownBy(this::create).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.categories",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs",Integer.class)).isEqualTo(1);
    }
    @Test void staleEditAndVisibilityCannotOverwriteNewerData() {
        var first = create();
        categories.update(first.id(),new CategoryRequest("New","new",first.version()));
        assertThatThrownBy(() -> categories.update(first.id(),new CategoryRequest("Old","old",first.version())))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThatThrownBy(() -> categories.setActive(first.id(),first.version(),false)).isInstanceOf(ApplicationException.class);
        assertThat(categories.get(first.id()).name()).isEqualTo("New");
        assertThat(categories.get(first.id()).active()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs",Integer.class)).isEqualTo(2);
    }
    @Test void invalidActorForeignKeyRollsBackBusinessWrite() {
        authenticate(Long.MAX_VALUE,"ADMIN");
        assertThatThrownBy(this::create).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.categories",Integer.class)).isZero();
    }
    @Test void untrustedPrincipalCannotBecomeAuditActor() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "42",null,List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThatThrownBy(this::create).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHENTICATED));
    }
    @Test void serviceRejectsNonOpsAndInvalidInputs() {
        for (String role : List.of("USER","VENDOR","SHIPPER")) {
            authenticate(actor,role);
            assertThatThrownBy(this::create).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> categories.list(0)).isInstanceOf(AccessDeniedException.class);
        }
        authenticate(actor,"ADMIN");
        assertThatThrownBy(() -> categories.create(new CategoryRequest(" ","../BAD",null)))
                .isInstanceOf(ConstraintViolationException.class);
        assertThatThrownBy(() -> categories.list(-1)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> categories.get(Long.MAX_VALUE)).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }
    @Test void databaseConstraintsAndRepeatMigrationAreValid() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.categories(name,slug) VALUES (' ','bad')"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.categories(name,slug) VALUES ('Name','BAD')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void listingIsBoundedAndStable() {
        for (int i=0; i<21; i++) categories.create(new CategoryRequest("Category " + i,"cat-" + i,null));
        assertThat(categories.list(0).getContent()).hasSize(20);
        assertThat(categories.list(1).getContent()).hasSize(1);
        assertThat(categories.list(0).getContent().getFirst().id())
                .isGreaterThan(categories.list(1).getContent().getFirst().id());
    }
}
