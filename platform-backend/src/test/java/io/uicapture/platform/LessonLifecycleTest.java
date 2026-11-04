package io.uicapture.platform;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.uicapture.platform.app.DemoDataInitializer;
import io.uicapture.platform.entity.*;
import io.uicapture.platform.service.AccessException;
import io.uicapture.platform.service.LessonImportService;
import io.uicapture.platform.service.LessonLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class LessonLifecycleTest {

    @Autowired
    LessonImportService importService;
    @Autowired
    LessonLifecycleService lifecycle;
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

    private UUID importAs(String username, String id) {
        return auth.withUser(username, () -> importService.importPackage(TestPackages.validZip(id, "ЖЦ " + id), null, "test").lesson().getId());
    }

    private Lesson move(String username, UUID lessonId, LessonStatus to, String comment) {
        return auth.withUser(username, () -> lifecycle.transition(lessonId, to, comment));
    }

    private List<LifecycleTransition> journal(UUID lessonId) {
        return auth.withSystem(() -> dataManager.load(LifecycleTransition.class)
                .query("select t from uic_LifecycleTransition t where t.lesson.id = :l order by t.at, t.createdDate").parameter("l", lessonId).list());
    }

    @Test
    void trainerSelfApprovesOwnLessonAndJournalRecordsEveryStep() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-self"));
        move("trainer", lessonId, LessonStatus.REVIEW, null);
        Lesson published = move("trainer", lessonId, LessonStatus.PUBLISHED, "Самоутверждение");
        assertThat(published.getStatus()).isEqualTo(LessonStatus.PUBLISHED);
        assertThat(published.getPublishedVersion()).isNotNull();
        move("trainer", lessonId, LessonStatus.ARCHIVED, null);

        List<LifecycleTransition> entries = journal(lessonId);
        assertThat(entries).extracting(LifecycleTransition::getToStatus)
                .containsExactly(LessonStatus.DRAFT, LessonStatus.REVIEW, LessonStatus.PUBLISHED, LessonStatus.ARCHIVED);
        assertThat(entries).allSatisfy(t -> assertThat(t.getActor()).isEqualTo("trainer"));
        assertThat(entries.get(2).getFromStatus()).isEqualTo(LessonStatus.REVIEW);
        assertThat(entries.get(2).getComment()).isEqualTo("Самоутверждение");
    }

    @Test
    void reviewerReturnsToDraftOnlyWithComment() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-return"));
        move("trainer", lessonId, LessonStatus.REVIEW, null);
        assertThatThrownBy(() -> move("reviewer", lessonId, LessonStatus.DRAFT, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("комментарий");
        Lesson back = move("reviewer", lessonId, LessonStatus.DRAFT, "Поправьте подсказку шага 2");
        assertThat(back.getStatus()).isEqualTo(LessonStatus.DRAFT);
        LifecycleTransition last = journal(lessonId).get(journal(lessonId).size() - 1);
        assertThat(last.getActor()).isEqualTo("reviewer");
        assertThat(last.getComment()).isEqualTo("Поправьте подсказку шага 2");
        move("trainer", lessonId, LessonStatus.REVIEW, null);
        assertThat(move("reviewer", lessonId, LessonStatus.PUBLISHED, null).getStatus()).isEqualTo(LessonStatus.PUBLISHED);
    }

    @Test
    void forbiddenEdgesAndRoles() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-deny"));
        assertThatThrownBy(() -> move("trainer", lessonId, LessonStatus.PUBLISHED, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> move("trainer2", lessonId, LessonStatus.REVIEW, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("student", lessonId, LessonStatus.REVIEW, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("reviewer", lessonId, LessonStatus.REVIEW, null)).isInstanceOf(AccessException.class);
        move("trainer", lessonId, LessonStatus.REVIEW, null);
        assertThatThrownBy(() -> move("trainer2", lessonId, LessonStatus.PUBLISHED, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("student", lessonId, LessonStatus.PUBLISHED, null)).isInstanceOf(AccessException.class);
        assertThat(move("admin", lessonId, LessonStatus.PUBLISHED, null).getStatus()).isEqualTo(LessonStatus.PUBLISHED);
    }

    @Test
    void availableTransitionsDependOnRole() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-available"));
        Lesson lesson = auth.withSystem(() -> dataManager.load(Lesson.class).id(lessonId).one());
        assertThat(auth.withUser("trainer", () -> lifecycle.availableTransitions(lesson))).containsExactly(LessonStatus.REVIEW);
        assertThat(auth.withUser("reviewer", () -> lifecycle.availableTransitions(lesson))).isEmpty();
        move("trainer", lessonId, LessonStatus.REVIEW, null);
        assertThat(auth.withUser("reviewer", () -> lifecycle.availableTransitions(lesson)))
                .containsExactlyInAnyOrder(LessonStatus.PUBLISHED, LessonStatus.DRAFT);
        assertThat(auth.withUser("student", () -> lifecycle.availableTransitions(lesson))).isEmpty();
    }

    @Test
    void lessonVersionIsImmutable() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-immutable"));
        LessonVersion version = auth.withSystem(() -> dataManager.load(LessonVersion.class)
                .query("select v from uic_LessonVersion v where v.lesson.id = :l").parameter("l", lessonId).one());
        version.setTitle("Подмена");
        assertThatThrownBy(() -> auth.withSystem(() -> dataManager.save(version)))
                .hasMessageContaining("неизменяема");
    }
}
