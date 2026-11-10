package io.uitrainer.platform;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
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
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.ReviewerRole;
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
    void leadCannotMutateOrMoveLesson() {
        Course course = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Вести урок");
            c.setSharing(CourseSharing.ALL_TEACHERS);
            return dataManager.save(c);
        });
        UUID lessonId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("lead-mut"), "l"),
                    course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
        Lesson lesson = dataManager.unconstrained().load(Lesson.class).id(lessonId).one();
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            Lesson loaded = dataManager.load(Lesson.class).id(lesson.getId()).one();
            loaded.setTitle("хак");
            return dataManager.save(loaded);
        })).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            dataManager.remove(dataManager.load(Lesson.class).id(lesson.getId()).one());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            Lesson created = dataManager.create(Lesson.class);
            created.setExternalId(TestPackages.uniqueId("lead-new"));
            created.setTitle("новый");
            created.setAuthor(user("trainer2"));
            created.setCourse(course);
            created.setStatus(LessonStatus.DRAFT);
            return dataManager.save(created);
        })).isInstanceOf(AccessDeniedException.class);
        UUID ownId = auth.withUser("trainer2", () -> importService.importPackage(
                TestPackages.validZip(TestPackages.uniqueId("own-move"), "свой"), null, "test").lesson().getId());
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            Lesson own = dataManager.load(Lesson.class).id(ownId).one();
            own.setCourse(course);
            return dataManager.save(own);
        })).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void onlyOwnerDeletesAccessRows() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "Связи"));
        CourseCoauthor coauthor = auth.withUser("trainer", () -> {
            CourseCoauthor row = dataManager.create(CourseCoauthor.class);
            row.setCourse(course);
            row.setTrainer(user("trainer2"));
            return dataManager.save(row);
        });
        CourseSharedWith shared = auth.withUser("trainer", () -> {
            Course c = dataManager.load(Course.class).id(course.getId()).one();
            c.setSharing(CourseSharing.SELECTED);
            dataManager.save(c);
            CourseSharedWith row = dataManager.create(CourseSharedWith.class);
            row.setCourse(course);
            row.setTrainer(user("trainer2"));
            return dataManager.save(row);
        });
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            dataManager.remove(dataManager.load(CourseCoauthor.class).id(coauthor.getId()).one());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            dataManager.remove(dataManager.load(CourseSharedWith.class).id(shared.getId()).one());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.load(CourseCoauthor.class).id(coauthor.getId()).one());
            dataManager.remove(dataManager.load(CourseSharedWith.class).id(shared.getId()).one());
            return null;
        });
        assertThat(dataManager.unconstrained().load(CourseCoauthor.class).id(coauthor.getId()).optional()).isEmpty();
        assertThat(dataManager.unconstrained().load(CourseSharedWith.class).id(shared.getId()).optional()).isEmpty();
    }

    @Test
    void selectedThenNoneHidesFromTrainer2() {
        Course course = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Выбранным потом нет");
            c.setSharing(CourseSharing.SELECTED);
            c = dataManager.save(c);
            CourseSharedWith row = dataManager.create(CourseSharedWith.class);
            row.setCourse(c);
            row.setTrainer(user("trainer2"));
            dataManager.save(row);
            return c;
        });
        UUID lessonId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("sel-lsn"), "l"),
                    course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(course.getId()).optional())).isPresent();
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Lesson.class).id(lessonId).optional())).isPresent();
        auth.withUser("trainer", () -> {
            Course c = dataManager.load(Course.class).id(course.getId()).one();
            c.setSharing(CourseSharing.NONE);
            return dataManager.save(c);
        });
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Course.class).id(course.getId()).optional())).isEmpty();
        assertThat(auth.withUser("trainer2", () -> dataManager.load(Lesson.class).id(lessonId).optional())).isEmpty();
    }

    @Test
    void formerCoauthorCannotReimportOrTransition() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "Бывший соавтор"));
        String ext = TestPackages.uniqueId("ex-coa");
        auth.withUser("trainer", () -> {
            CourseCoauthor row = dataManager.create(CourseCoauthor.class);
            row.setCourse(course);
            row.setTrainer(user("trainer2"));
            return dataManager.save(row);
        });
        UUID lessonId = auth.withUser("trainer2", () -> importService.importPackage(
                TestPackages.validZip(ext, "coa"), course.getId(), "test").lesson().getId());
        CourseCoauthor row = dataManager.unconstrained().load(CourseCoauthor.class)
                .query("select a from uic_CourseCoauthor a where a.course = :c and a.trainer.username = :u")
                .parameter("c", course)
                .parameter("u", "trainer2")
                .one();
        auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.load(CourseCoauthor.class).id(row.getId()).one());
            return null;
        });
        assertThatThrownBy(() -> auth.withUser("trainer2", () ->
                importService.importPackage(TestPackages.validZip(ext, "v2"), course.getId(), "test")))
                .isInstanceOf(AccessException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> lifecycle.transition(lessonId, LessonStatus.PUBLISHED, null)))
                .isInstanceOf(AccessException.class);
    }

    @Test
    void ownerDeletesEmptyCourseWithAccessRows() {
        Course course = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "Пустой со связями");
            CourseCoauthor a = dataManager.create(CourseCoauthor.class);
            a.setCourse(c);
            a.setTrainer(user("trainer2"));
            dataManager.save(a);
            c.setSharing(CourseSharing.SELECTED);
            c = dataManager.save(c);
            CourseSharedWith s = dataManager.create(CourseSharedWith.class);
            s.setCourse(c);
            s.setTrainer(user("trainer2"));
            dataManager.save(s);
            return c;
        });
        UUID id = course.getId();
        auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.load(Course.class).id(id).one());
            return null;
        });
        assertThat(dataManager.unconstrained().load(Course.class).id(id).optional()).isEmpty();
        assertThat(dataManager.unconstrained().loadValue(
                        "select count(a) from uic_CourseCoauthor a where a.course.id = :c", Long.class)
                .parameter("c", id).one()).isZero();
        assertThat(dataManager.unconstrained().loadValue(
                        "select count(s) from uic_CourseSharedWith s where s.course.id = :c", Long.class)
                .parameter("c", id).one()).isZero();
    }

    @Test
    void formerOwnerCannotDeleteAfterAdminReassign() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "Смена владельца"));
        auth.withUser("admin", () -> {
            Course c = dataManager.load(Course.class).id(course.getId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME)).one();
            c.setOwner(user("trainer2"));
            return dataManager.save(c);
        });
        assertThatThrownBy(() -> auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.unconstrained().load(Course.class).id(course.getId()).one());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void reviewerCannotRenameForeignLessonButCanPublishViaLifecycle() {
        Course course = auth.withUser("trainer", () -> {
            Course c = saveCourse("trainer", "ревью обход");
            c.setSharing(CourseSharing.ALL_TEACHERS);
            return dataManager.save(c);
        });
        UUID publishedId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("rev-pub"), "p"),
                    course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
        UUID reviewId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(TestPackages.uniqueId("rev-q"), "q"),
                    course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.REVIEW, null);
            return id;
        });
        grantRole("trainer2", ReviewerRole.CODE, "resource");
        try {
            assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
                Lesson loaded = dataManager.load(Lesson.class).id(publishedId).one();
                loaded.setTitle("хак");
                return dataManager.save(loaded);
            })).isInstanceOf(AccessDeniedException.class);
            auth.withUser("trainer2", () -> lifecycle.transition(reviewId, LessonStatus.PUBLISHED, null));
            assertThat(dataManager.unconstrained().load(Lesson.class).id(reviewId).one().getStatus())
                    .isEqualTo(LessonStatus.PUBLISHED);
        } finally {
            revokeRole("trainer2", ReviewerRole.CODE);
        }
    }

    @Test
    void unassignedLessonOnlyAuthorOrAdmin() {
        UUID lessonId = auth.withUser("trainer", () -> importService.importPackage(
                TestPackages.validZip(TestPackages.uniqueId("orphan"), "свой без курса"), null, "test")
                .lesson().getId());
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            Lesson loaded = dataManager.unconstrained().load(Lesson.class).id(lessonId).one();
            loaded.setTitle("чужой");
            return dataManager.save(loaded);
        })).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            dataManager.remove(dataManager.unconstrained().load(Lesson.class).id(lessonId).one());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        auth.withUser("trainer", () -> {
            Lesson loaded = dataManager.load(Lesson.class).id(lessonId).one();
            loaded.setTitle("автор");
            return dataManager.save(loaded);
        });
        assertThat(dataManager.unconstrained().load(Lesson.class).id(lessonId).one().getTitle()).isEqualTo("автор");
    }

    @Test
    void lessonVersionNeedsLoadedLessonAndCannotMove() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "версии"));
        UUID lessonId = auth.withUser("trainer", () -> importService.importPackage(
                TestPackages.validZip(TestPackages.uniqueId("ver-src"), "src"), course.getId(), "test")
                .lesson().getId());
        UUID otherId = auth.withUser("trainer", () -> importService.importPackage(
                TestPackages.validZip(TestPackages.uniqueId("ver-dst"), "dst"), course.getId(), "test")
                .lesson().getId());
        UUID versionId = dataManager.unconstrained().load(Lesson.class).id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("currentVersion", FetchPlan.BASE))
                .one().getCurrentVersion().getId();
        LessonVersion unloaded = dataManager.unconstrained().load(LessonVersion.class).id(versionId)
                .fetchPlan(FetchPlan.BASE)
                .one();
        unloaded.setTitle("хак версии");
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> dataManager.save(unloaded)))
                .isInstanceOf(AccessDeniedException.class);
        Lesson other = dataManager.unconstrained().load(Lesson.class).id(otherId).one();
        LessonVersion loaded = dataManager.unconstrained().load(LessonVersion.class).id(versionId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("lesson", FetchPlan.BASE))
                .one();
        loaded.setLesson(other);
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> dataManager.save(loaded)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sharingPersists() {
        Course course = auth.withUser("trainer", () -> saveCourse("trainer", "Sharing save"));
        auth.withUser("trainer", () -> {
            Course c = dataManager.load(Course.class).id(course.getId()).one();
            c.setSharing(CourseSharing.ALL_TEACHERS);
            return dataManager.save(c);
        });
        Course loaded = dataManager.unconstrained().load(Course.class).id(course.getId()).one();
        assertThat(loaded.getSharing()).isEqualTo(CourseSharing.ALL_TEACHERS);
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

    private void grantRole(String username, String role, String type) {
        auth.withSystem(() -> {
            boolean exists = dataManager.load(RoleAssignmentEntity.class)
                    .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :c")
                    .parameter("u", username).parameter("c", role).optional().isPresent();
            if (exists) {
                return null;
            }
            RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
            assignment.setUsername(username);
            assignment.setRoleCode(role);
            assignment.setRoleType(type);
            dataManager.save(assignment);
            return null;
        });
    }

    private void revokeRole(String username, String role) {
        auth.withSystem(() -> {
            dataManager.load(RoleAssignmentEntity.class)
                    .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :c")
                    .parameter("u", username).parameter("c", role)
                    .list()
                    .forEach(dataManager::remove);
            return null;
        });
    }
}
