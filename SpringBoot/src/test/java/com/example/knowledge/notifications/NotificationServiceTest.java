package com.example.knowledge.notifications;

import com.example.knowledge.common.BusinessException;
import com.example.knowledge.security.JwtPrincipal;
import com.example.knowledge.service.PermissionService;
import org.junit.jupiter.api.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import java.time.LocalDateTime;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    EmbeddedDatabase db;
    JdbcTemplate jdbc;
    NotificationService service;
    PermissionService permissions;
    JwtPrincipal owner = new JwtPrincipal(1L, "owner", "USER");
    JwtPrincipal stranger = new JwtPrincipal(2L, "stranger", "USER");

    @BeforeEach void setup() {
        db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("CREATE TABLE sys_user(id BIGINT PRIMARY KEY, role VARCHAR(20), status INT)");
        jdbc.execute("CREATE TABLE knowledge_base(id BIGINT PRIMARY KEY, owner_id BIGINT, name VARCHAR(100), status INT, shared BOOLEAN DEFAULT FALSE)");
        jdbc.execute("CREATE TABLE knowledge_member(knowledge_base_id BIGINT,user_id BIGINT,role VARCHAR(20))");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(new FileSystemResource("../sql/notifications.sql")).execute(db);
        jdbc.update("INSERT INTO sys_user VALUES(1, 'USER', 1), (2, 'USER', 1), (3, 'ADMIN', 1)");
        jdbc.update("INSERT INTO knowledge_base(id, owner_id, name, status) VALUES(10, 1, '资料库', 1)");
        permissions = mock(PermissionService.class);
        service = new NotificationService(jdbc, permissions);
    }
    @AfterEach void close() { db.shutdown(); }
    KnowledgeUpdate event(String key, LocalDateTime time) { return new KnowledgeUpdate(key, 10L, 20L, "讲义.txt", "UPLOADED", time); }

    @Test void followingDoesNotBackfillAndDuplicateDeliveryIsIdempotent() {
        service.record(event("before-follow", LocalDateTime.now().minusDays(1)));
        service.follow(10L, owner);
        service.follow(10L, owner);
        assertThat(service.list(owner, false, 1, 20).total()).isZero();
        KnowledgeUpdate next = event("next", LocalDateTime.now().plusSeconds(1));
        service.record(next); service.record(next);
        assertThat(service.unreadCount(owner)).isEqualTo(1);
        assertThat(service.list(stranger, false, 1, 20).total()).isZero();
        service.unfollow(10L, owner);
        service.record(event("after-unfollow", LocalDateTime.now().plusSeconds(1)));
        assertThat(service.unreadCount(owner)).isEqualTo(1);
    }

    @Test void delayedOldEventDoesNotReachNewSubscription() {
        KnowledgeUpdate old = event("old", LocalDateTime.now().minusSeconds(2));
        service.follow(10L, owner);
        service.record(old);
        assertThat(service.unreadCount(owner)).isZero();
        service.unfollow(10L, owner);
        service.follow(10L, owner);
        service.record(event("delayed-before-refollow", LocalDateTime.now().minusSeconds(1)));
        assertThat(service.unreadCount(owner)).isZero();
    }

    @Test void readTransitionsArePrivateAndRevokedAccessIsHidden() {
        service.follow(10L, owner);
        service.record(event("one", LocalDateTime.now().plusSeconds(1)));
        service.record(event("two", LocalDateTime.now().plusSeconds(1)));
        long id = service.list(owner, false, 1, 20).items().getFirst().id();
        assertThatThrownBy(() -> service.markRead(id, stranger)).isInstanceOf(BusinessException.class);
        service.markRead(id, owner); service.markRead(id, owner);
        assertThat(service.unreadCount(owner)).isEqualTo(1);
        service.markAllRead(owner);
        assertThat(service.list(owner, true, 1, 20).total()).isZero();
        jdbc.update("UPDATE knowledge_base SET owner_id=2 WHERE id=10");
        assertThat(service.list(owner, false, 1, 20).total()).isZero();
        assertThatThrownBy(() -> service.markRead(id, owner)).isInstanceOf(BusinessException.class);
    }

    @Test void adminDowngradeAndKnowledgeDeletionHideExistingNotifications() {
        JwtPrincipal admin = new JwtPrincipal(3L, "admin", "ADMIN");
        service.follow(10L, admin);
        service.record(event("admin-event", LocalDateTime.now().plusSeconds(1)));
        assertThat(service.unreadCount(admin)).isOne();
        jdbc.update("UPDATE sys_user SET role='USER' WHERE id=3");
        assertThat(service.unreadCount(new JwtPrincipal(3L, "admin", "USER"))).isZero();
        service.record(event("after-downgrade", LocalDateTime.now().plusSeconds(1)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE user_id=3", Long.class)).isOne();
        service.follow(10L, owner);
        service.record(event("owner-event", LocalDateTime.now().plusSeconds(1)));
        jdbc.update("DELETE FROM knowledge_base WHERE id=10");
        assertThat(service.unreadCount(owner)).isZero();
    }

    @Test void sharedAdminKnowledgeNotifiesReadersAndWithdrawalRevokesAccess() {
        jdbc.update("UPDATE knowledge_base SET owner_id=3,shared=TRUE WHERE id=10");
        service.follow(10L, stranger);
        service.record(event("shared-admin", LocalDateTime.now().plusSeconds(1)));
        assertThat(service.unreadCount(stranger)).isOne();
        long id = service.list(stranger, false, 1, 20).items().getFirst().id();
        service.markRead(id, stranger);
        assertThat(service.unreadCount(stranger)).isZero();
        jdbc.update("UPDATE knowledge_base SET shared=FALSE WHERE id=10");
        assertThat(service.list(stranger, false, 1, 20).total()).isZero();
        service.record(event("after-creator-downgrade", LocalDateTime.now().plusSeconds(1)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE user_id=2", Long.class)).isOne();
    }

    @Test void userSharingNotifiesReadersAndWithdrawalHidesNotifications() {
        jdbc.update("UPDATE knowledge_base SET shared=TRUE WHERE id=10");
        service.follow(10L, stranger);
        service.record(event("user-shared", LocalDateTime.now().plusSeconds(1)));
        assertThat(service.unreadCount(stranger)).isOne();
        jdbc.update("UPDATE knowledge_base SET shared=FALSE WHERE id=10");
        assertThat(service.list(stranger, false, 1, 20).total()).isZero();
        service.record(event("withdrawn", LocalDateTime.now().plusSeconds(1)));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_notification WHERE user_id=2", Long.class)).isOne();
    }

    @Test void subscriptionRequiresKnowledgeAccess() {
        doThrow(new BusinessException(com.example.knowledge.common.ErrorCode.FORBIDDEN))
                .when(permissions).requireKnowledgeBaseAccess(10L, stranger, false);
        assertThatThrownBy(() -> service.follow(10L, stranger)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.subscription(10L, stranger)).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_subscription", Long.class)).isZero();
    }

    @Test void privateMembersReceiveUpdatesAndRevocationHidesExistingOnes() {
        jdbc.update("INSERT INTO knowledge_member VALUES (10,2,'VIEWER')");
        service.follow(10L,stranger); service.record(event("member-update",LocalDateTime.now().plusSeconds(1)));
        assertThat(service.unreadCount(stranger)).isOne();
        jdbc.update("DELETE FROM knowledge_member WHERE user_id=2");
        assertThat(service.unreadCount(stranger)).isZero();
    }
    @Test void restoringDocumentIsAnUpdateEvent() {
        service.follow(10L,owner);
        service.record(new KnowledgeUpdate("restored",10L,20L,"讲义.txt","RESTORED",LocalDateTime.now().plusSeconds(1)));
        assertThat(service.unreadCount(owner)).isOne();
    }
}
