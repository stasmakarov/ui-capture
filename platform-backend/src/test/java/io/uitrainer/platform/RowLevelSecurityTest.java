package io.uitrainer.platform;

import io.jmix.core.DataManager;
import io.jmix.core.DeletePolicyException;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseSharing;
import io.uitrainer.platform.entity.GroupAccessMode;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.StudioToken;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.TrainerOwnDataRole;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.security.UiMinimalRole;
import io.uitrainer.platform.service.LessonImportService;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.service.StudioTokenService;
import io.uitrainer.platform.service.UserProvisioning;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    @Autowired
    PasswordEncoder passwordEncoder;

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
        String directId = TestPackages.uniqueId("rls-direct");
        String reviewId = TestPackages.uniqueId("rls-review");
        String returnedId = TestPackages.uniqueId("rls-returned");
        auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(draftId, "Черновик"), null, null));
        UUID pub = auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(publishedId, "Опубликован"), null, null)).lesson().getId();
        auth.withUser("trainer", () -> lifecycle.transition(pub, LessonStatus.REVIEW, null));
        auth.withUser("trainer", () -> lifecycle.transition(pub, LessonStatus.PUBLISHED, null));
        UUID direct = auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(directId, "Сразу"), null, null)).lesson().getId();
        auth.withUser("trainer", () -> lifecycle.transition(direct, LessonStatus.PUBLISHED, null));
        UUID review = auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(reviewId, "На проверке"), null, null)).lesson().getId();
        auth.withUser("trainer", () -> lifecycle.transition(review, LessonStatus.REVIEW, null));
        UUID returned = auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(returnedId, "Возврат"), null, null)).lesson().getId();
        auth.withUser("trainer", () -> lifecycle.transition(returned, LessonStatus.REVIEW, null));
        auth.withUser("reviewer", () -> lifecycle.transition(returned, LessonStatus.DRAFT, "Поправьте шаг"));

        assertThat(visibleLessonIds("trainer")).contains(draftId, publishedId, directId, reviewId, returnedId);
        assertThat(visibleLessonIds("trainer2")).doesNotContain(draftId, publishedId, directId, reviewId, returnedId);
        assertThat(visibleLessonIds("reviewer")).contains(draftId, publishedId, directId, reviewId, returnedId);
        assertThat(visibleLessonIds("student")).contains(publishedId, directId).doesNotContain(draftId, reviewId, returnedId);
        assertThat(visibleLessonIds("admin")).contains(draftId, publishedId, directId, reviewId, returnedId);
        assertThat(auth.withUser("student", () -> dataManager.load(Lesson.class).all().list()))
                .allSatisfy(lesson -> assertThat(lesson.getStatus()).isEqualTo(LessonStatus.PUBLISHED));
    }

    @Test
    void trainerSeesOwnCoursesReviewerAndAdminSeeAll() {
        Course own = auth.withUser("trainer", () -> saveCourse("trainer", "Свой курс"));
        Course other = auth.withUser("trainer2", () -> saveCourse("trainer2", "Чужой курс"));
        String lessonId = TestPackages.uniqueId("course-lesson");
        auth.withUser("trainer", () -> importService.importPackage(TestPackages.validZip(lessonId, "Урок курса"), own.getId(), null));

        assertThat(visibleCourseCodes("trainer")).contains(DemoDataInitializer.DEMO_COURSE_CODE, own.getCode()).doesNotContain(other.getCode());
        assertThat(visibleCourseCodes("trainer2")).contains(other.getCode(), DemoDataInitializer.DEMO_COURSE_CODE, DemoDataInitializer.DEMO_COURSE_2_CODE)
                .doesNotContain(own.getCode());
        assertThat(visibleCourseCodes("reviewer")).contains(DemoDataInitializer.DEMO_COURSE_CODE, own.getCode(), other.getCode());
        assertThat(visibleCourseCodes("admin")).contains(own.getCode(), other.getCode());

        assertThat(lessonIdsOf(own.getId(), "trainer")).contains(lessonId);
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(own.getId()).optional())).isEmpty();
        assertThat(lessonIdsOf(own.getId(), "reviewer")).contains(lessonId);

        assertThat(visibleCourseCodes("student")).doesNotContain(own.getCode(), other.getCode());
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

    private User user(String username) {
        return dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", username).one();
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
    void studentSeesPublishedLessonsOfLeadCourseInGroup() {
        Course course = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Ведение для группы");
            c.setSharing(CourseSharing.ALL_TEACHERS);
            return dataManager.save(c);
        });
        String lessonExt = TestPackages.uniqueId("lead-stu");
        UUID lessonId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(lessonExt, "урок ведения"),
                    course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
        auth.withUser("trainer", () -> {
            StudyGroup group = dataManager.create(StudyGroup.class);
            group.setName("lead-stu-" + TestPackages.uniqueId("g"));
            group.setOwner(user("trainer"));
            group.setStatus(GroupStatus.ACTIVE);
            group.setAccessMode(GroupAccessMode.ALL_AT_ONCE);
            group = dataManager.save(group);
            GroupMember member = dataManager.create(GroupMember.class);
            member.setGroup(group);
            member.setStudent(user("student"));
            dataManager.save(member);
            GroupCourse line = dataManager.create(GroupCourse.class);
            line.setGroup(group);
            line.setCourse(course);
            line.setSortOrder(1);
            dataManager.save(line);
            return null;
        });
        assertThat(auth.withUser("student", () -> dataManager.load(Lesson.class).id(lessonId).optional())).isPresent();
        assertThat(visibleLessonIds("student")).contains(lessonExt);
    }

    @Test
    void assigningTrainerResourceRoleSyncsOwnDataAndHidesForeignDraft() {
        String login = "sync-" + TestPackages.uniqueId("u");
        String draftExt = TestPackages.uniqueId("foreign-draft");
        UUID draftId = auth.withUser("trainer", () ->
                importService.importPackage(TestPackages.validZip(draftExt, "Чужой черновик"), null, null)
                        .lesson().getId());
        auth.withSystem(() -> {
            User user = dataManager.create(User.class);
            user.setUsername(login);
            user.setFirstName("Синх");
            user.setLastName("Ролей");
            user.setPassword(passwordEncoder.encode(login));
            user.setActive(true);
            dataManager.save(user);
            RoleAssignmentEntity minimal = dataManager.create(RoleAssignmentEntity.class);
            minimal.setUsername(login);
            minimal.setRoleCode(UiMinimalRole.CODE);
            minimal.setRoleType(UserProvisioning.RESOURCE);
            dataManager.save(minimal);
            RoleAssignmentEntity trainer = dataManager.create(RoleAssignmentEntity.class);
            trainer.setUsername(login);
            trainer.setRoleCode(TrainerRole.CODE);
            trainer.setRoleType(UserProvisioning.RESOURCE);
            dataManager.save(trainer);
            return null;
        });
        boolean ownData = auth.withSystem(() -> dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :c and r.roleType = :t")
                .parameter("u", login)
                .parameter("c", TrainerOwnDataRole.CODE)
                .parameter("t", UserProvisioning.ROW_LEVEL)
                .optional()
                .isPresent());
        assertThat(ownData).isTrue();
        assertThat(auth.withUser(login, () -> dataManager.load(Lesson.class).id(draftId).optional())).isEmpty();
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
