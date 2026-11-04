package io.uicapture.platform;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.uicapture.platform.app.DemoDataInitializer;
import io.uicapture.platform.entity.Lesson;
import io.uicapture.platform.entity.LessonStatus;
import io.uicapture.platform.entity.StudioToken;
import io.uicapture.platform.service.LessonImportService;
import io.uicapture.platform.service.LessonLifecycleService;
import io.uicapture.platform.service.StudioTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Ресурсные и row-level роли: кто какие уроки и токены видит через обычный (защищённый) DataManager. */
@SpringBootTest
@ActiveProfiles("test")
class RowLevelSecurityTest {

    @Autowired
    LessonImportService importService;
    @Autowired
    LessonLifecycleService lifecycle;
    @Autowired
    StudioTokenService tokenService;
    @Autowired
    SystemAuthenticator auth;
    @Autowired
    DataManager dataManager;
    @Autowired
    DemoDataInitializer demoData;

    @BeforeEach
    void users() {
        demoData.init();
    }

    private List<String> visibleLessonIds(String username) {
        return auth.withUser(username, () -> dataManager.load(Lesson.class).all().list())
                .stream().map(Lesson::getExternalId).toList();
    }

    @Test
    void trainerSeesOwnReviewerSeesAllStudentSeesPublished() {
        String draftId = TestPackages.uniqueId("rls-draft");
        String publishedId = TestPackages.uniqueId("rls-pub");
        auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(draftId, "Черновик"), null, null));
        UUID pub = auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(publishedId, "Опубликован"), null, null)).lesson().getId();
        auth.withUser("trainer", () -> lifecycle.transition(pub, LessonStatus.REVIEW, null));
        auth.withUser("trainer", () -> lifecycle.transition(pub, LessonStatus.PUBLISHED, null));

        assertThat(visibleLessonIds("trainer")).contains(draftId, publishedId);
        assertThat(visibleLessonIds("trainer2")).doesNotContain(draftId, publishedId);
        assertThat(visibleLessonIds("reviewer")).contains(draftId, publishedId);
        assertThat(visibleLessonIds("student")).contains(publishedId).doesNotContain(draftId);
        assertThat(visibleLessonIds("admin")).contains(draftId, publishedId);
    }

    @Test
    void tokensAreVisibleOnlyToOwner() {
        StudioTokenService.IssuedToken issued = auth.withUser("trainer", () -> tokenService.issueToken("rls", null));
        List<UUID> mine = auth.withUser("trainer", () -> dataManager.load(StudioToken.class).all().list()).stream().map(StudioToken::getId).toList();
        List<UUID> other = auth.withUser("trainer2", () -> dataManager.load(StudioToken.class).all().list()).stream().map(StudioToken::getId).toList();
        assertThat(mine).contains(issued.token().getId());
        assertThat(other).doesNotContain(issued.token().getId());
        assertThat(issued.token().getTokenHash()).doesNotContain(issued.plainText()).hasSize(64);
    }
}
