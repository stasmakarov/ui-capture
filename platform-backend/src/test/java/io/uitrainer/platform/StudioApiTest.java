package io.uitrainer.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.entity.*;
import io.uitrainer.platform.service.StudioTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudioApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    StudioTokenService tokenService;
    @Autowired
    SystemAuthenticator systemAuthenticator;
    @Autowired
    DataManager dataManager;
    @Autowired
    DemoDataInitializer demoData;
    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper();

    private String tokenFor(String username) {
        demoData.init();
        return systemAuthenticator.withUser(username, () -> tokenService.issueToken("test", null).plainText());
    }

    private JsonNode importZip(String token, byte[] zip, int expectedStatus) throws Exception {
        MvcResult result = mvc.perform(post("/api/lessons/import")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/zip").content(zip))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    void importWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/lessons/import").contentType("application/zip").content(TestPackages.validZip("api-noauth", "x")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/api/lessons/import").header("Authorization", "Bearer uicst_wrong")
                        .contentType("application/zip").content(TestPackages.validZip("api-noauth", "x")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void importCreatesDraftWithJsonbFilesAndStepRefs() throws Exception {
        String id = TestPackages.uniqueId("api-import");
        JsonNode body = importZip(tokenFor("trainer"), TestPackages.validZip(id, "Импорт из студии"), 201);
        assertThat(body.path("status").asText()).isEqualTo("DRAFT");
        assertThat(body.path("versionNo").asInt()).isEqualTo(1);
        assertThat(body.path("newLesson").asBoolean()).isTrue();

        Lesson lesson = systemAuthenticator.withSystem(() -> dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.externalId = :id").parameter("id", id)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("currentVersion", "_base")).one());
        assertThat(lesson.getStatus()).isEqualTo(LessonStatus.DRAFT);
        assertThat(lesson.getAuthor().getUsername()).isEqualTo("trainer");
        LessonVersion version = lesson.getCurrentVersion();
        assertThat(version.getStepCount()).isEqualTo(3);
        assertThat(version.getTrainerHtml()).isNotNull();
        assertThat(mapper.readTree(version.getContent()).path("id").asText()).isEqualTo(id);

        // lesson.json действительно лежит в JSONB: запрос по полю внутри документа
        String viaJsonb = jdbc.queryForObject("select content ->> 'id' from uic_lesson_version where id = ?", String.class, version.getId());
        assertThat(viaJsonb).isEqualTo(id);
        String columnType = jdbc.queryForObject("select data_type from information_schema.columns where table_name = 'uic_lesson_version' and column_name = 'content'", String.class);
        assertThat(columnType).isEqualTo("jsonb");

        List<StepRef> steps = systemAuthenticator.withSystem(() -> dataManager.load(StepRef.class)
                .query("select s from uic_StepRef s where s.lessonVersion.id = :v order by s.index").parameter("v", version.getId()).list());
        assertThat(steps).extracting(StepRef::getStepId).containsExactly("step-1", "step-2", "step-3");
        List<LessonAsset> assets = systemAuthenticator.withSystem(() -> dataManager.load(LessonAsset.class)
                .query("select a from uic_LessonAsset a where a.lessonVersion.id = :v").parameter("v", version.getId()).list());
        assertThat(assets).extracting(LessonAsset::getPath).contains("lesson.json", "screens/screen-1.html", "original/screen-1.png", "trainer.html");
    }

    @Test
    void hashMismatchIs400WithReadableError() throws Exception {
        String id = TestPackages.uniqueId("api-hash");
        Map<String, byte[]> files = TestPackages.lessonFiles(id, "Хэш");
        JsonNode body = importZip(tokenFor("trainer"), TestPackages.zip(id, "Хэш", files, "lesson.json", null), 400);
        assertThat(body.path("error").asText()).contains("Хэш lesson.json не совпадает с манифестом");
    }

    @Test
    void reimportCreatesNewVersionAndReturnsToDraft() throws Exception {
        String id = TestPackages.uniqueId("api-reimport");
        String token = tokenFor("trainer");
        importZip(token, TestPackages.validZip(id, "Версия 1"), 201);
        JsonNode second = importZip(token, TestPackages.validZip(id, "Версия 2"), 201);
        assertThat(second.path("versionNo").asInt()).isEqualTo(2);
        assertThat(second.path("newLesson").asBoolean()).isFalse();
        assertThat(second.path("title").asText()).isEqualTo("Версия 2");
    }

    @Test
    void multipartUploadWorks() throws Exception {
        String id = TestPackages.uniqueId("api-multipart");
        mvc.perform(multipart("/api/lessons/import")
                        .file(new MockMultipartFile("file", id + ".lesson.zip", "application/zip", TestPackages.validZip(id, "Multipart")))
                        .header("Authorization", "Bearer " + tokenFor("trainer")))
                .andExpect(status().isCreated());
    }

    @Test
    void anotherTrainerCannotOverwriteLessonAndStudentCannotImport() throws Exception {
        String id = TestPackages.uniqueId("api-owner");
        importZip(tokenFor("trainer"), TestPackages.validZip(id, "Чужой"), 201);
        JsonNode denied = importZip(tokenFor("trainer2"), TestPackages.validZip(id, "Перехват"), 403);
        assertThat(denied.path("error").asText()).contains("другому автору");
        importZip(tokenFor("student"), TestPackages.validZip(TestPackages.uniqueId("api-student"), "Нет"), 403);
    }

    @Test
    void revokedTokenIsRejected() throws Exception {
        demoData.init();
        StudioTokenService.IssuedToken issued = systemAuthenticator.withUser("trainer", () -> tokenService.issueToken("revoke", null));
        systemAuthenticator.withUser("trainer", () -> {
            tokenService.revoke(issued.token().getId());
            return null;
        });
        importZip(issued.plainText(), TestPackages.validZip(TestPackages.uniqueId("api-revoked"), "x"), 401);
    }

    @Test
    void ticketIsExchangedOnceForWorkingToken() throws Exception {
        demoData.init();
        Course course = systemAuthenticator.withSystem(() -> dataManager.load(Course.class)
                .query("select c from uic_Course c where c.code = :c").parameter("c", DemoDataInitializer.DEMO_COURSE_CODE).one());
        String ticket = systemAuthenticator.withUser("trainer", () -> tokenService.issueTicket(course.getId()));
        MvcResult result = mvc.perform(post("/api/studio/exchange").contentType("application/json")
                        .content("{\"ticket\":\"" + ticket + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("trainer"))
                .andExpect(jsonPath("$.courseId").value(course.getId().toString()))
                .andReturn();
        String token = mapper.readTree(result.getResponse().getContentAsByteArray()).path("token").asText();
        assertThat(token).startsWith("uicst_");
        mvc.perform(get("/api/studio/whoami").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("trainer"));
        mvc.perform(post("/api/studio/exchange").contentType("application/json").content("{\"ticket\":\"" + ticket + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("уже использован")));

        String id = TestPackages.uniqueId("api-course");
        mvc.perform(post("/api/lessons/import").header("Authorization", "Bearer " + token)
                        .header("X-Course-Id", course.getId().toString())
                        .contentType("application/zip").content(TestPackages.validZip(id, "С курсом")))
                .andExpect(status().isCreated());
        Lesson lesson = systemAuthenticator.withSystem(() -> dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.externalId = :id").parameter("id", id)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("course", "_base")).one());
        assertThat(lesson.getCourse().getId()).isEqualTo(course.getId());
    }
}
