package ru.ugk.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.ugk.schedule.bot.BotUpdateInbox;
import static org.assertj.core.api.Assertions.*;

/** Use a dedicated test database. Each run creates and removes its own schema. */
@EnabledIfEnvironmentVariable(named="UGK_TEST_DB_URL",matches=".+")
@SpringBootTest(properties={"spring.config.import=", "app.telegram.token=", "app.max.token=",
    "app.admin.username=integration-admin", "app.admin.password=integration-secret", "spring.profiles.active="})
class PostgresIntegrationTest {
    private static final String SCHEMA="ugk_test_"+UUID.randomUUID().toString().replace("-","");
    @Autowired BotUpdateInbox inbox;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url",() -> System.getenv("UGK_TEST_DB_URL"));
        properties.add("spring.datasource.username",() -> System.getenv("UGK_TEST_DB_USERNAME"));
        properties.add("spring.datasource.password",() -> System.getenv("UGK_TEST_DB_PASSWORD"));
        properties.add("spring.flyway.schemas",() -> SCHEMA);
        properties.add("spring.flyway.default-schema",() -> SCHEMA);
        properties.add("spring.jpa.properties.hibernate.default_schema",() -> SCHEMA);
        properties.add("spring.datasource.hikari.connection-init-sql",() -> "set search_path to "+SCHEMA);
    }
    @AfterAll static void cleanup(@Autowired JdbcTemplate jdbc) { jdbc.execute("drop schema "+SCHEMA+" cascade"); }
    @Test void fullApplicationStartsAndFlywayMigrates() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success=true and version is not null",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from admin_users where username='integration-admin'",Integer.class)).isEqualTo(1);
    }
    @Test void upgradingExistingSchemaPreservesScheduleAndSupportsOldMigrations() throws Exception {
        String schema=SCHEMA+"_upgrade";
        java.nio.file.Path legacy=java.nio.file.Files.createTempDirectory("ugk-legacy-migrations");
        try {
            for(String file:java.util.List.of("V1__init.sql","V2__seed_catalog.sql")) {
                try(var input=getClass().getResourceAsStream("/db/migration/"+file)) {
                    java.nio.file.Files.copy(input,legacy.resolve(file));
                }
            }
            var old=org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource())
                .schemas(schema).defaultSchema(schema).locations("filesystem:"+legacy).load();
            old.migrate();
            long level=jdbc.queryForObject("insert into "+schema+".education_levels(name,max_courses) values ('upgrade-test',1) returning id",Long.class);
            long course=jdbc.queryForObject("insert into "+schema+".courses(education_level_id,number,name) values (?,1,'course') returning id",Long.class,level);
            long group=jdbc.queryForObject("insert into "+schema+".study_groups(course_id,name) values (?,'group') returning id",Long.class,course);
            jdbc.update("insert into "+schema+".schedule_entries(group_id,day_of_week,start_time,subject) values (?,'MONDAY','09:00','preserved')",group);
            org.flywaydb.core.Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema).defaultSchema(schema).load().migrate();
            assertThat(jdbc.queryForObject("select subject from "+schema+".schedule_entries where group_id=?",String.class,group)).isEqualTo("preserved");
            assertThatCode(old::validate).doesNotThrowAnyException();
        } finally {
            jdbc.execute("drop schema if exists "+schema+" cascade");
            for(String file:java.util.List.of("V1__init.sql","V2__seed_catalog.sql")) java.nio.file.Files.deleteIfExists(legacy.resolve(file));
            java.nio.file.Files.deleteIfExists(legacy);
        }
    }
    @Test void inboxSurvivesServiceRecreationAndDeduplicates() throws Exception {
        String stream="telegram:"+UUID.randomUUID();
        var updates=mapper.readTree("[{\"update_id\":1,\"message\":{\"from\":{\"id\":7}}}]");
        inbox.capture(stream,2L,updates);inbox.capture(stream,2L,updates);
        var restarted=new BotUpdateInbox(jdbc,mapper);
        assertThat(restarted.cursor(stream)).isEqualTo(2L);
        assertThat(restarted.pending(stream,100)).hasSize(1);
        long id=restarted.pending(stream,100).get(0).id();
        restarted.complete(id);
        assertThat(jdbc.queryForObject("select payload from bot_updates where id=?",String.class,id)).isNull();
        inbox.capture(stream,2L,updates);
        assertThat(inbox.pending(stream,100)).isEmpty();
    }
    @Test void failedCaptureRollsBackWholeBatchAndCursor() throws Exception {
        String stream="telegram:"+UUID.randomUUID();
        inbox.capture(stream,1L,mapper.readTree("[]"));
        var updates=mapper.createArrayNode();updates.addObject().put("update_id",1);
        updates.addObject().put("update_id","x".repeat(65));
        assertThatThrownBy(() -> inbox.capture(stream,99L,updates)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(inbox.cursor(stream)).isEqualTo(1L);
        assertThat(inbox.pending(stream,100)).isEmpty();
    }
    @Test void retriesRespectUserOrderAndKeepDeadLetters() throws Exception {
        String stream="telegram:"+UUID.randomUUID();
        inbox.capture(stream,4L,mapper.readTree("[{\"update_id\":1,\"message\":{\"from\":{\"id\":7}}},"+
            "{\"update_id\":2,\"message\":{\"from\":{\"id\":7}}},{\"update_id\":3,\"message\":{\"from\":{\"id\":8}}}]") );
        var pending=inbox.pending(stream,100);assertThat(pending).hasSize(2);
        long failed=pending.get(0).id();inbox.failed(failed,new IllegalStateException());
        assertThat(inbox.pending(stream,100)).extracting(BotUpdateInbox.Update::id).containsExactly(pending.get(1).id());
        for(int i=0;i<4;i++) inbox.failed(failed,new IllegalStateException());
        assertThat(jdbc.queryForObject("select attempts from bot_updates where id=?",Integer.class,failed)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select payload from bot_updates where id=?",String.class,failed)).isNotNull();
    }
}
