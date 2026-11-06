package io.uicapture.platform;

import io.jmix.core.DataManager;
import io.jmix.core.DeletePolicyException;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.uicapture.platform.app.DemoDataInitializer;
import io.uicapture.platform.entity.Course;
import io.uicapture.platform.entity.Lesson;
import io.uicapture.platform.entity.LessonStatus;
import io.uicapture.platform.entity.StudioToken;
import io.uicapture.platform.entity.User;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ресурсные и row-level роли: кто какие уроки, курсы и токены видит через обычный (защищённый) DataManager. */
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
    void trainerSeesOwnCoursesReviewerAndAdminSeeAll() {
        Course own = auth.withUser("trainer", () -> saveCourse("trainer", "Свой курс"));
        Course other = auth.withUser("trainer2", () -> saveCourse("trainer2", "Чужой курс"));
        String lessonId = TestPackages.uniqueId("course-lesson");
        auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(lessonId, "Урок курса"), own.getId(), null));

        assertThat(visibleCourseCodes("trainer")).contains(DemoDataInitializer.DEMO_COURSE_CODE, own.getCode()).doesNotContain(other.getCode());
        assertThat(visibleCourseCodes("trainer2")).contains(other.getCode()).doesNotContain(own.getCode(), DemoDataInitializer.DEMO_COURSE_CODE);
        assertThat(visibleCourseCodes("reviewer")).contains(DemoDataInitializer.DEMO_COURSE_CODE, own.getCode(), other.getCode());
        assertThat(visibleCourseCodes("admin")).contains(own.getCode(), other.getCode());

        assertThat(lessonIdsOf(own.getId(), "trainer")).contains(lessonId);
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(own.getId()).optional())).isEmpty();
        assertThat(lessonIdsOf(own.getId(), "reviewer")).contains(lessonId);

        assertThatThrownBy(() -> visibleCourseCodes("student")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void courseWithLessonsIsNotDeleted() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "Курс с уроком"));
        auth.withUser("trainer", () -> importService.importPackage(
                TestPackages.validZip(TestPackages.uniqueId("course-keep"), "Не удалять"), course.getId(), null));
        assertThatThrownBy(() -> auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.load(Course.class).id(course.getId()).one());
            return null;
        })).isInstanceOf(DeletePolicyException.class);

        Course empty = auth.withUser("trainer", () -> saveCourse("trainer", "Пустой курс"));
        auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.load(Course.class).id(empty.getId()).one());
            return null;
        });
        assertThat(auth.withUser("admin", () -> dataManager.load(Course.class).id(empty.getId()).optional())).isEmpty();
    }

    private Course saveCourse(String ownerUsername, String title) {
        User owner = dataManager.load(User.class).query("select u from User u where u.username = :u")
                .parameter("u", ownerUsername).one();
        Course course = dataManager.create(Course.class);
        course.setCode(TestPackages.uniqueId("course"));
        course.setTitle(title);
        course.setOwner(owner);
        return dataManager.save(course);
    }

    private List<String> visibleCourseCodes(String username) {
        return auth.withUser(username, () -> dataManager.load(Course.class).all().list())
                .stream().map(Course::getCode).toList();
    }

    private List<String> lessonIdsOf(UUID courseId, String username) {
        return auth.withUser(username, () -> dataManager.load(Course.class).id(courseId)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("lessons", "_base")).one()
                .getLessons().stream().map(Lesson::getExternalId).toList());
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
