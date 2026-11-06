package io.uitrainer.platform;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.entity.*;
import io.uitrainer.platform.service.AccessException;
import io.uitrainer.platform.service.LessonImportService;
import io.uitrainer.platform.service.LessonLifecycleService;
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
    void trainerPublishesDraftWithoutReview() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-direct"));
        Lesson published = move("trainer", lessonId, LessonStatus.PUBLISHED, null);
        assertThat(published.getStatus()).isEqualTo(LessonStatus.PUBLISHED);
        assertThat(published.getPublishedVersion()).isNotNull();
        assertThat(published.getPublishedVersion().getId()).isEqualTo(published.getCurrentVersion().getId());

        List<LifecycleTransition> entries = journal(lessonId);
        assertThat(entries).extracting(LifecycleTransition::getToStatus)
                .containsExactly(LessonStatus.DRAFT, LessonStatus.PUBLISHED);
        assertThat(entries.get(1).getFromStatus()).isEqualTo(LessonStatus.DRAFT);
        assertThat(entries.get(1).getActor()).isEqualTo("trainer");
        assertThat(entries.get(1).getComment()).isNull();
    }

    @Test
    void republishAddsVersionAndNextPublishPointsAtIt() {
        String externalId = TestPackages.uniqueId("lc-repub");
        UUID lessonId = importAs("trainer", externalId);
        Lesson first = move("trainer", lessonId, LessonStatus.PUBLISHED, null);
        assertThat(first.getPublishedVersion().getVersionNo()).isEqualTo(1);

        auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(externalId, "Версия 2"), null, "test"));
        Lesson draft = auth.withSystem(() -> dataManager.load(Lesson.class).id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("currentVersion", "_base").add("publishedVersion", "_base")).one());
        assertThat(draft.getStatus()).isEqualTo(LessonStatus.DRAFT);
        assertThat(draft.getCurrentVersion().getVersionNo()).isEqualTo(2);

        Lesson again = move("trainer", lessonId, LessonStatus.PUBLISHED, null);
        assertThat(again.getStatus()).isEqualTo(LessonStatus.PUBLISHED);
        assertThat(again.getPublishedVersion().getVersionNo()).isEqualTo(2);
        assertThat(again.getCurrentVersion().getId()).isEqualTo(again.getPublishedVersion().getId());
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
        assertThatThrownBy(() -> move("trainer", lessonId, LessonStatus.ARCHIVED, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> move("trainer2", lessonId, LessonStatus.PUBLISHED, null))
                .isInstanceOf(AccessException.class).hasMessageContaining("автор");
        assertThatThrownBy(() -> move("trainer2", lessonId, LessonStatus.REVIEW, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("student", lessonId, LessonStatus.PUBLISHED, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("student", lessonId, LessonStatus.REVIEW, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("reviewer", lessonId, LessonStatus.PUBLISHED, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("reviewer", lessonId, LessonStatus.REVIEW, null)).isInstanceOf(AccessException.class);
        assertThat(move("admin", lessonId, LessonStatus.PUBLISHED, null).getStatus()).isEqualTo(LessonStatus.PUBLISHED);
        UUID inReview = importAs("trainer", TestPackages.uniqueId("lc-deny-review"));
        move("trainer", inReview, LessonStatus.REVIEW, null);
        assertThatThrownBy(() -> move("trainer2", inReview, LessonStatus.PUBLISHED, null)).isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> move("student", inReview, LessonStatus.PUBLISHED, null)).isInstanceOf(AccessException.class);
        assertThat(move("admin", inReview, LessonStatus.PUBLISHED, null).getStatus()).isEqualTo(LessonStatus.PUBLISHED);
    }

    @Test
    void availableTransitionsDependOnRole() {
        UUID lessonId = importAs("trainer", TestPackages.uniqueId("lc-available"));
        Lesson lesson = auth.withSystem(() -> dataManager.load(Lesson.class).id(lessonId).one());
        assertThat(auth.withUser("trainer", () -> lifecycle.availableTransitions(lesson)))
                .containsExactly(LessonStatus.PUBLISHED, LessonStatus.REVIEW);
        assertThat(auth.withUser("reviewer", () -> lifecycle.availableTransitions(lesson))).isEmpty();
        assertThat(auth.withUser("admin", () -> lifecycle.availableTransitions(lesson)))
                .containsExactly(LessonStatus.PUBLISHED, LessonStatus.REVIEW);
        move("trainer", lessonId, LessonStatus.REVIEW, null);
        assertThat(auth.withUser("trainer", () -> lifecycle.availableTransitions(lesson)))
                .containsExactly(LessonStatus.PUBLISHED, LessonStatus.DRAFT);
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
