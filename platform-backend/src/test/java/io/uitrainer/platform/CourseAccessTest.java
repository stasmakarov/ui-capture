package io.uitrainer.platform;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseCoauthor;
import io.uitrainer.platform.entity.CourseSharing;
import io.uitrainer.platform.entity.CourseSharedWith;
import io.uitrainer.platform.entity.GroupAccessMode;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.service.AccessException;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.LessonImportService;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.service.LessonOrderService;
import io.uitrainer.platform.service.StudioTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CourseAccessTest {

    @Autowired
    SystemAuthenticator auth;
    @Autowired
    DataManager dataManager;
    @Autowired
    DemoDataInitializer demoData;
    @Autowired
    LessonImportService importService;
    @Autowired
    LessonLifecycleService lifecycle;
    @Autowired
    LessonOrderService lessonOrder;
    @Autowired
    StudioTokenService tokenService;
    @Autowired
    CurrentAuthentication currentAuthentication;

    @BeforeEach
    void users() {
        demoData.init();
    }

    @Test
    void adminAddsForeignCourseToTrainerGroup() {
        Course foreign = auth.withUser("trainer2", () -> saveCourse("trainer2", "Чужой для админа"));
        StudyGroup group = auth.withUser("trainer", () -> emptyGroup("admin-gc"));
        auth.withUser("admin", () -> {
            GroupCourse line = dataManager.create(GroupCourse.class);
            line.setGroup(group);
            line.setCourse(foreign);
            line.setSortOrder(1);
            dataManager.save(line);
            return null;
        });
        assertThat(dataManager.unconstrained().load(GroupCourse.class)
                .query("select gc from uic_GroupCourse gc where gc.group = :g and gc.course = :c")
                .parameter("g", group)
                .parameter("c", foreign)
                .optional()).isPresent();
    }

    @Test
    void trainer2SharingNoneAllSelected() {
        Course none = auth.withUser("trainer", () -> saveCourse("trainer", "Только владелец"));
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(none.getId()).optional())).isEmpty();

        Course all = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Всем");
            c.setSharing(CourseSharing.ALL_TEACHERS);
            return dataManager.save(c);
        });
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(all.getId()).optional())).isPresent();
        StudyGroup g2 = auth.withUser("trainer2", () -> emptyGroup("lead-all"));
        auth.withUser("trainer2", () -> {
            GroupCourse line = dataManager.create(GroupCourse.class);
            line.setGroup(g2);
            line.setCourse(all);
            line.setSortOrder(1);
            return dataManager.save(line);
        });

        Course selected = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Выбранным");
            c.setSharing(CourseSharing.SELECTED);
            return dataManager.save(c);
        });
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(selected.getId()).optional())).isEmpty();
        auth.withUser("trainer", () -> {
            CourseSharedWith row = dataManager.create(CourseSharedWith.class);
            row.setCourse(selected);
            row.setTrainer(user("trainer2"));
            return dataManager.save(row);
        });
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(selected.getId()).optional())).isPresent();
    }

    @Test
    void leadCannotEditImportReorderPublishOrTicket() {
        Course course = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Вести нельзя править");
            c.setSharing(CourseSharing.ALL_TEACHERS);
            return dataManager.save(c);
        });
        UUID lessonId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("lead-lsn"), "l"),
                    course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
        Lesson lesson = dataManager.unconstrained().load(Lesson.class).id(lessonId).one();
        assertThatThrownBy(() -> auth.withUser("trainer2", () ->
                importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("lead-imp"), "x"),
                        course.getId(), "test")))
                .isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            lessonOrder.moveLesson(course, lesson, 1);
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> lifecycle.transition(lessonId, LessonStatus.ARCHIVED, null)))
                .isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> tokenService.issueTicket(course.getId())))
                .isInstanceOf(AccessException.class);
    }

    @Test
    void coauthorEditsButCannotManageOrDelete() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "С соавтором"));
        auth.withUser("trainer", () -> {
            CourseCoauthor row = dataManager.create(CourseCoauthor.class);
            row.setCourse(course);
            row.setTrainer(user("trainer2"));
            return dataManager.save(row);
        });
        auth.withUser("trainer2", () -> importService.importPackage(
                TestPackages.validZip(TestPackages.uniqueId("coa-imp"), "coa"), course.getId(), "test"));
        Lesson lesson = dataManager.unconstrained().load(Lesson.class)
                .query("select l from uic_Lesson l where l.course = :c")
                .parameter("c", course)
                .one();
        auth.withUser("trainer2", () -> {
            lessonOrder.moveLesson(course, lesson, 1);
            return null;
        });
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            CourseCoauthor extra = dataManager.create(CourseCoauthor.class);
            extra.setCourse(course);
            extra.setTrainer(user("trainer"));
            return dataManager.save(extra);
        })).isInstanceOf(AccessDeniedException.class);
        Course empty = auth.withUser("trainer", () -> saveCourse("trainer", "Пустой соавтор"));
        auth.withUser("trainer", () -> {
            CourseCoauthor row = dataManager.create(CourseCoauthor.class);
            row.setCourse(empty);
            row.setTrainer(user("trainer2"));
            return dataManager.save(row);
        });
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            dataManager.remove(dataManager.load(Course.class).id(empty.getId()).one());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void directorSeesAndCannotChange() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "Для директора"));
        assertThat(auth.withUser("director", () -> dataManager.load(Course.class).id(course.getId()).optional())).isPresent();
        assertThatThrownBy(() -> auth.withUser("director", () -> {
            Course loaded = dataManager.load(Course.class).id(course.getId()).one();
            loaded.setTitle("нельзя");
            return dataManager.save(loaded);
        })).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.withUser("director", () ->
                importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("dir-imp"), "x"),
                        course.getId(), "test")))
                .isInstanceOf(AccessException.class);
    }

    @Test
    void cannotAssignWithoutAccess() {
        Course none = auth.withUser("trainer", () -> saveCourse("trainer", "Чужой none"));
        StudyGroup g2 = auth.withUser("trainer2", () -> emptyGroup("no-access"));
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            GroupCourse line = dataManager.create(GroupCourse.class);
            line.setGroup(g2);
            line.setCourse(none);
            line.setSortOrder(1);
            return dataManager.save(line);
        })).isInstanceOf(ConflictException.class)
                .hasMessageContaining("недоступен вам для групп");
    }

    private Course saveCourse(String ownerUsername, String title) {
        User owner = user(ownerUsername);
        Course course = dataManager.create(Course.class);
        course.setCode(TestPackages.uniqueId("acc"));
        course.setTitle(title);
        course.setOwner(owner);
        course.setSharing(CourseSharing.NONE);
        return dataManager.save(course);
    }

    private StudyGroup emptyGroup(String name) {
        StudyGroup group = dataManager.create(StudyGroup.class);
        group.setName(name + "-" + UUID.randomUUID().toString().substring(0, 8));
        group.setOwner(user(currentAuthentication.getUser().getUsername()));
        group.setStatus(GroupStatus.CREATED);
        group.setAccessMode(GroupAccessMode.ALL_AT_ONCE);
        return dataManager.save(group);
    }

    private User user(String username) {
        return dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", username)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.INSTANCE_NAME).add("username"))
                .one();
    }
}
