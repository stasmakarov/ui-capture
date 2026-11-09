package io.uitrainer.platform;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseProgressStatus;
import io.uitrainer.platform.entity.GroupAccessMode;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupCourseProgress;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonAttempt;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.service.GroupLifecycleService;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.service.LessonImportService;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.service.LessonProgressService;
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
class GroupProgressTest {

    @Autowired
    LessonImportService importService;
    @Autowired
    LessonLifecycleService lifecycle;
    @Autowired
    LessonProgressService lessonProgress;
    @Autowired
    GroupProgressService groupProgress;
    @Autowired
    GroupLifecycleService groupLifecycle;
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

    @Test
    void sequentialLocksSecondUntilFirstCompleted() {
        Course first = course("Первый");
        Course second = course("Второй");
        UUID firstLesson = publishLesson(first, "seq-first");
        publishLesson(second, "seq-second");
        StudyGroup group = sequentialGroup("seq-lock", first, second);

        assertThat(accessibleTitles(group)).containsExactly("Первый");

        auth.withUser("student", () -> {
            lessonProgress.completeLesson(firstLesson);
            return null;
        });

        assertThat(accessibleTitles(group)).containsExactly("Первый", "Второй");
    }

    @Test
    void otherGroupSeesNextCourseFromGlobalAttempts() {
        Course first = course("Общий первый");
        Course second = course("Общий второй");
        UUID firstLesson = publishLesson(first, "global-first");
        publishLesson(second, "global-second");
        sequentialGroup("seq-a", first, second);

        auth.withUser("student", () -> {
            lessonProgress.completeLesson(firstLesson);
            return null;
        });

        StudyGroup other = sequentialGroup("seq-b", first, second);
        assertThat(accessibleTitles(other)).containsExactly("Общий первый", "Общий второй");
    }

    @Test
    void emptyCourseDoesNotBlockNext() {
        Course empty = course("Пустой");
        Course first = course("С уроком");
        Course second = course("Следующий");
        UUID firstLesson = publishLesson(first, "empty-skip-first");
        publishLesson(second, "empty-skip-second");
        StudyGroup group = sequentialGroup("seq-empty", empty, first, second);

        assertThat(accessibleTitles(group)).containsExactly("С уроком");

        auth.withUser("student", () -> {
            lessonProgress.completeLesson(firstLesson);
            return null;
        });

        assertThat(accessibleTitles(group)).containsExactly("С уроком", "Следующий");
    }

    @Test
    void completedSnapshotDoesNotReopenAfterNewLesson() {
        Course course = course("Снимок");
        UUID lesson = publishLesson(course, "snap-one");
        StudyGroup group = sequentialGroup("seq-snap", course);

        auth.withUser("student", () -> {
            lessonProgress.completeLesson(lesson);
            return null;
        });
        assertThat(groupProgress.status(group, student(), course)).isEqualTo(CourseProgressStatus.COMPLETED);

        publishLesson(course, "snap-two");
        groupProgress.recalculate(group, student(), course);

        assertThat(groupProgress.status(group, student(), course)).isEqualTo(CourseProgressStatus.COMPLETED);
    }

    @Test
    void studentKeepsPinnedVersionAfterRepublish() {
        Course course = course("Закрепление");
        String externalId = TestPackages.uniqueId("pin");
        UUID lessonId = auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(TestPackages.validZip(externalId, "v1"), course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
        sequentialGroup("seq-pin", course);

        LessonAttempt attempt = auth.withUser("student", () -> lessonProgress.startLesson(lessonId));
        UUID v1 = attempt.getLessonVersion().getId();

        auth.withUser("trainer", () -> {
            importService.importPackage(TestPackages.validZip(externalId, "v2"), course.getId(), "test");
            lifecycle.transition(lessonId, LessonStatus.PUBLISHED, null);
            return null;
        });

        Lesson published = auth.withSystem(() -> dataManager.load(Lesson.class).id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("publishedVersion", FetchPlan.BASE)).one());
        assertThat(published.getPublishedVersion().getId()).isNotEqualTo(v1);

        LessonVersion pinned = auth.withUser("student", () -> dataManager.load(LessonVersion.class).id(v1)
                .fetchPlan(FetchPlan.BASE).one());
        assertThat(pinned.getId()).isEqualTo(v1);
    }

    @Test
    void trainerStudentDirectorSeeOwnGroups() {
        StudyGroup mine = sequentialGroup("vis-mine", course("Видимый"));
        StudyGroup other = auth.withUser("trainer2", () -> {
            Course c = saveCourse("trainer2", "Чужой курс группы");
            return saveGroup("vis-other", GroupAccessMode.ALL_AT_ONCE, List.of(c));
        });

        List<String> trainerNames = auth.withUser("trainer", () -> dataManager.load(StudyGroup.class).all().list())
                .stream().map(StudyGroup::getName).toList();
        assertThat(trainerNames).contains(mine.getName()).doesNotContain(other.getName());

        List<String> trainer2Names = auth.withUser("trainer2", () -> dataManager.load(StudyGroup.class).all().list())
                .stream().map(StudyGroup::getName).toList();
        assertThat(trainer2Names).contains(other.getName()).doesNotContain(mine.getName());

        List<String> studentNames = auth.withUser("student", () -> dataManager.load(StudyGroup.class).all().list())
                .stream().map(StudyGroup::getName).toList();
        assertThat(studentNames).contains(mine.getName(), DemoDataInitializer.DEMO_GROUP_NAME).doesNotContain(other.getName());

        List<String> directorNames = auth.withUser("director", () -> dataManager.load(StudyGroup.class).all().list())
                .stream().map(StudyGroup::getName).toList();
        assertThat(directorNames).contains(mine.getName(), other.getName());

        assertThatThrownBy(() -> auth.withUser("director", () -> {
            StudyGroup loaded = dataManager.load(StudyGroup.class).id(mine.getId()).one();
            loaded.setName("нельзя");
            return dataManager.save(loaded);
        })).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removeMemberCleansProgressAndSummaryStaysAtMostHundredPercent() {
        Course course = course("Чистка");
        UUID lesson = publishLesson(course, "clean-one");
        StudyGroup group = sequentialGroup("seq-clean", course);

        auth.withUser("student", () -> {
            lessonProgress.completeLesson(lesson);
            return null;
        });
        assertThat(progressCount(group.getId(), student().getId())).isEqualTo(1);

        GroupMember member = auth.withUser("trainer", () -> dataManager.load(GroupMember.class)
                .query("select m from uic_GroupMember m where m.group.id = :g and m.student.id = :s")
                .parameter("g", group.getId())
                .parameter("s", student().getId())
                .one());
        auth.withUser("trainer", () -> {
            dataManager.remove(member);
            return null;
        });

        assertThat(progressCount(group.getId(), student().getId())).isZero();
        assertThat(auth.withSystem(() -> dataManager.load(LessonAttempt.class)
                .query("select a from uic_LessonAttempt a where a.student.id = :s and a.lesson.id = :l")
                .parameter("s", student().getId())
                .parameter("l", lesson)
                .optional())).isPresent();

        GroupProgressService.GroupSummary summary = groupProgress.summarize(group);
        assertThat(summary.completedPairs()).isLessThanOrEqualTo(summary.totalPairs());
        if (summary.totalPairs() > 0) {
            assertThat(summary.completedPairs() * 100 / summary.totalPairs()).isLessThanOrEqualTo(100);
        }
    }

    private List<String> accessibleTitles(StudyGroup group) {
        return groupProgress.accessibleCourses(group, student()).stream()
                .map(gc -> gc.getCourse().getTitle())
                .toList();
    }

    private long progressCount(UUID groupId, UUID studentId) {
        Long count = dataManager.unconstrained().loadValue(
                        "select count(p) from uic_GroupCourseProgress p where p.group.id = :g and p.student.id = :s",
                        Long.class)
                .parameter("g", groupId)
                .parameter("s", studentId)
                .one();
        return count == null ? 0 : count;
    }

    private StudyGroup sequentialGroup(String name, Course... courses) {
        return auth.withUser("trainer", () -> {
            StudyGroup group = saveGroup(name, GroupAccessMode.SEQUENTIAL, List.of(courses));
            GroupMember member = dataManager.create(GroupMember.class);
            member.setGroup(group);
            member.setStudent(student());
            dataManager.save(member);
            return groupLifecycle.activate(group.getId());
        });
    }

    private StudyGroup saveGroup(String name, GroupAccessMode mode, List<Course> courses) {
        User owner = user(ownerUsername(courses.get(0)));
        StudyGroup group = dataManager.create(StudyGroup.class);
        group.setName(name + "-" + UUID.randomUUID().toString().substring(0, 8));
        group.setOwner(owner);
        group.setStatus(GroupStatus.CREATED);
        group.setAccessMode(mode);
        group = dataManager.save(group);
        int order = 1;
        for (Course course : courses) {
            GroupCourse line = dataManager.create(GroupCourse.class);
            line.setGroup(group);
            line.setCourse(course);
            line.setSortOrder(order++);
            dataManager.save(line);
        }
        return group;
    }

    private String ownerUsername(Course course) {
        Course loaded = dataManager.unconstrained().load(Course.class).id(course.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME))
                .one();
        return loaded.getOwner().getUsername();
    }

    private Course course(String title) {
        return auth.withUser("trainer", () -> saveCourse("trainer", title));
    }

    private Course saveCourse(String ownerUsername, String title) {
        User owner = user(ownerUsername);
        Course course = dataManager.create(Course.class);
        course.setCode(TestPackages.uniqueId("gpc"));
        course.setTitle(title);
        course.setOwner(owner);
        return dataManager.save(course);
    }

    private UUID publishLesson(Course course, String prefix) {
        return auth.withUser("trainer", () -> {
            UUID id = importService.importPackage(
                    TestPackages.validZip(TestPackages.uniqueId(prefix), prefix), course.getId(), "test").lesson().getId();
            lifecycle.transition(id, LessonStatus.PUBLISHED, null);
            return id;
        });
    }

    private User student() {
        return user("student");
    }

    private User user(String username) {
        return dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", username)
                .one();
    }
}
