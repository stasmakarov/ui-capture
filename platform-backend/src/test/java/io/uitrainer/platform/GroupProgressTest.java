package io.uitrainer.platform;

import io.jmix.core.AccessManager;
import io.jmix.core.DataManager;
import io.jmix.core.DeletePolicyException;
import io.jmix.core.FetchPlan;
import io.jmix.core.Metadata;
import io.jmix.core.SaveContext;
import io.jmix.core.accesscontext.CrudEntityContext;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.flowui.accesscontext.UiShowViewContext;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
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
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.security.StudentPublishedRole;
import io.uitrainer.platform.security.StudentRole;
import io.uitrainer.platform.security.UiMinimalRole;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.GroupLifecycleService;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.service.LessonAttemptInsert;
import io.uitrainer.platform.service.LessonImportService;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.service.LessonOrderService;
import io.uitrainer.platform.service.LessonProgressService;
import io.uitrainer.platform.service.SortOrders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
    @Autowired
    LessonOrderService lessonOrder;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    LessonAttemptInsert attemptInsert;
    @Autowired
    AccessManager accessManager;
    @Autowired
    Metadata metadata;

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

        assertThat(auth.withUser("trainer", this::groupListPermitted)).isTrue();
        assertThat(auth.withUser("director", this::groupListPermitted)).isTrue();
        assertThat(auth.withUser("student", this::groupListPermitted)).isFalse();
        assertThat(auth.withUser("trainer", this::groupCreatePermitted)).isTrue();
        assertThat(auth.withUser("director", this::groupCreatePermitted)).isFalse();
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

    @Test
    void sequentialFollowsSavedCourseOrder() {
        Course first = course("Порядок-1");
        Course second = course("Порядок-2");
        Course third = course("Порядок-3");
        publishLesson(first, "ord-1");
        UUID secondLesson = publishLesson(second, "ord-2");
        publishLesson(third, "ord-3");
        StudyGroup group = auth.withUser("trainer", () -> {
            StudyGroup saved = saveGroupTogether("seq-order", GroupAccessMode.SEQUENTIAL, List.of(first, second, third));
            GroupMember member = dataManager.create(GroupMember.class);
            member.setGroup(saved);
            member.setStudent(student());
            dataManager.save(member);
            return groupLifecycle.activate(saved.getId());
        });

        assertThat(sortOrders(group)).containsExactly(1, 2, 3);
        assertThat(accessibleTitles(group)).containsExactly("Порядок-1");

        List<GroupCourse> ordered = groupProgress.orderedCourses(group);
        SortOrders.move(ordered, ordered.get(0), 1);
        SortOrders.normalize(ordered, GroupCourse::setSortOrder);
        auth.withUser("trainer", () -> dataManager.save(new SaveContext().saving(ordered.toArray())));

        assertThat(sortOrders(group)).containsExactly(1, 2, 3);
        assertThat(groupProgress.orderedCourses(group).stream().map(gc -> gc.getCourse().getTitle()))
                .containsExactly("Порядок-2", "Порядок-1", "Порядок-3");
        assertThat(accessibleTitles(group)).containsExactly("Порядок-2");

        auth.withUser("student", () -> {
            lessonProgress.completeLesson(secondLesson);
            return null;
        });
        assertThat(accessibleTitles(group)).containsExactly("Порядок-2", "Порядок-1");

        GroupCourse middle = groupProgress.orderedCourses(group).get(1);
        auth.withUser("trainer", () -> {
            dataManager.remove(middle);
            return null;
        });
        List<GroupCourse> left = groupProgress.orderedCourses(group);
        assertThat(left.stream().map(GroupCourse::getSortOrder)).containsExactly(1, 2);
        assertThat(left.stream().map(gc -> gc.getCourse().getTitle())).containsExactly("Порядок-2", "Порядок-3");
    }

    @Test
    void concurrentStartLessonKeepsOneAttempt() throws InterruptedException {
        Course course = course("Гонка");
        UUID lessonId = publishLesson(course, "race-one");
        sequentialGroup("seq-race", course);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Runnable task = () -> {
            try {
                auth.withUser("student", () -> {
                    ready.countDown();
                    try {
                        if (!go.await(10, TimeUnit.SECONDS)) {
                            throw new IllegalStateException("timeout");
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(ex);
                    }
                    return lessonProgress.startLesson(lessonId);
                });
            } catch (Throwable ex) {
                error.compareAndSet(null, ex);
            }
        };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(task);
        pool.submit(task);
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
        assertThat(error.get()).isNull();
        Long count = dataManager.unconstrained().loadValue(
                        "select count(a) from uic_LessonAttempt a where a.student.id = :s and a.lesson.id = :l",
                        Long.class)
                .parameter("s", student().getId())
                .parameter("l", lessonId)
                .one();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void startLessonAfterDuplicateInsertReturnsExisting() {
        Course course = course("Дубль старт");
        UUID lessonId = publishLesson(course, "dup-start");
        sequentialGroup("seq-dup-start", course);
        LessonAttempt first = auth.withUser("student", () -> lessonProgress.startLesson(lessonId));
        Lesson lesson = dataManager.unconstrained().load(Lesson.class).id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("publishedVersion", FetchPlan.BASE))
                .one();
        LessonAttempt duplicate = dataManager.unconstrained().create(LessonAttempt.class);
        duplicate.setStudent(student());
        duplicate.setLesson(lesson);
        duplicate.setLessonVersion(lesson.getPublishedVersion());
        duplicate.setStartedAt(first.getStartedAt());
        assertThatThrownBy(() -> attemptInsert.insert(duplicate)).isNotNull();
        LessonAttempt again = auth.withUser("student", () -> lessonProgress.startLesson(lessonId));
        assertThat(again.getId()).isEqualTo(first.getId());
        Long count = dataManager.unconstrained().loadValue(
                        "select count(a) from uic_LessonAttempt a where a.student.id = :s and a.lesson.id = :l",
                        Long.class)
                .parameter("s", student().getId())
                .parameter("l", lessonId)
                .one();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void cannotReplaceStudentOrCourseOnExistingLine() {
        Course first = course("Нельзя курс");
        Course other = course("Другой курс");
        StudyGroup group = sequentialGroup("seq-immut", first);
        GroupCourse line = groupProgress.orderedCourses(group).get(0);
        line.setCourse(other);
        assertThatThrownBy(() -> auth.withUser("trainer", () -> dataManager.save(line)))
                .isInstanceOf(ConflictException.class);

        User otherStudent = extraStudent();
        GroupMember member = auth.withUser("trainer", () -> dataManager.load(GroupMember.class)
                .query("select m from uic_GroupMember m where m.group.id = :g and m.student.id = :s")
                .parameter("g", group.getId())
                .parameter("s", student().getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("group", FetchPlan.BASE)
                        .add("student", FetchPlan.BASE))
                .one());
        member.setStudent(otherStudent);
        assertThatThrownBy(() -> auth.withUser("trainer", () -> dataManager.save(member)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void sequentialBlocksDirectLessonAndPreview() {
        Course first = course("Открытый");
        Course second = course("Закрытый");
        UUID firstLesson = publishLesson(first, "open-seq");
        UUID secondLesson = publishLesson(second, "lock-seq");
        sequentialGroup("seq-direct", first, second);
        Lesson locked = dataManager.unconstrained().load(Lesson.class).id(secondLesson)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("course", FetchPlan.BASE)
                        .add("publishedVersion", FetchPlan.BASE))
                .one();
        assertThat(groupProgress.canStudentOpenLesson(student(), locked)).isFalse();
        assertThat(auth.withUser("student", () -> {
            LessonVersion version = dataManager.load(LessonVersion.class).id(locked.getPublishedVersion().getId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("lesson", FetchPlan.BASE))
                    .optional().orElse(null);
            return version != null && groupProgress.canStudentOpenLesson(student(), version.getLesson());
        })).isFalse();
        Lesson open = dataManager.unconstrained().load(Lesson.class).id(firstLesson)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE))
                .one();
        assertThat(groupProgress.canStudentOpenLesson(student(), open)).isTrue();
    }

    @Test
    void courseInGroupCannotBeDeleted() {
        Course course = course("В группе");
        sequentialGroup("seq-deny-course", course);
        UUID courseId = course.getId();
        assertThatThrownBy(() -> auth.withUser("trainer", () -> {
            dataManager.remove(dataManager.load(Course.class).id(courseId).one());
            return null;
        })).isInstanceOf(DeletePolicyException.class);
        assertThat(dataManager.unconstrained().load(Course.class).id(courseId).optional()).isPresent();
    }

    @Test
    void removeCourseClearsProgress() {
        Course course = course("Сводка курс");
        UUID lesson = publishLesson(course, "sum-course");
        StudyGroup group = sequentialGroup("seq-rm-course", course);
        auth.withUser("student", () -> {
            lessonProgress.completeLesson(lesson);
            return null;
        });
        GroupCourse line = groupProgress.orderedCourses(group).get(0);
        auth.withUser("trainer", () -> {
            dataManager.remove(line);
            return null;
        });
        assertThat(dataManager.unconstrained().loadValue(
                        "select count(p) from uic_GroupCourseProgress p where p.group.id = :g and p.course.id = :c",
                        Long.class)
                .parameter("g", group.getId())
                .parameter("c", course.getId())
                .one()).isZero();
    }

    @Test
    void summaryAfterRemovingOneOfTwoStudents() {
        Course course = course("Сводка ученики");
        UUID lesson = publishLesson(course, "sum-two");
        User other = extraStudent();
        StudyGroup group = sequentialGroup("seq-sum-two", course);
        auth.withUser("trainer", () -> {
            GroupMember member = dataManager.create(GroupMember.class);
            member.setGroup(group);
            member.setStudent(other);
            dataManager.save(member);
            return null;
        });
        auth.withUser("student", () -> {
            lessonProgress.completeLesson(lesson);
            return null;
        });
        auth.withUser(other.getUsername(), () -> {
            lessonProgress.completeLesson(lesson);
            return null;
        });
        GroupMember firstMember = auth.withUser("trainer", () -> dataManager.load(GroupMember.class)
                .query("select m from uic_GroupMember m where m.group.id = :g and m.student.id = :s")
                .parameter("g", group.getId())
                .parameter("s", student().getId())
                .one());
        auth.withUser("trainer", () -> {
            dataManager.remove(firstMember);
            return null;
        });
        GroupProgressService.GroupSummary summary = groupProgress.summarize(group);
        assertThat(summary.members()).isEqualTo(1);
        assertThat(summary.totalPairs()).isGreaterThan(0);
        assertThat(summary.completedPairs()).isNotZero();
        assertThat(summary.completedPairs()).isLessThanOrEqualTo(summary.totalPairs());
    }

    @Test
    void studentDoesNotSeeClosedGroup() {
        StudyGroup group = sequentialGroup("seq-closed", course("Закрытая"));
        String name = group.getName();
        UUID id = group.getId();
        auth.withUser("trainer", () -> groupLifecycle.close(id));
        assertThat(auth.withUser("student", () -> dataManager.load(StudyGroup.class).id(id).optional())).isEmpty();
        List<String> names = auth.withUser("student", () -> dataManager.load(StudyGroup.class).all().list())
                .stream().map(StudyGroup::getName).toList();
        assertThat(names).doesNotContain(name);
    }

    @Test
    void createGroupWithMembersAndCoursesInOneSave() {
        Course first = course("Один сейв 1");
        Course second = course("Один сейв 2");
        StudyGroup saved = auth.withUser("trainer", () -> {
            User owner = user("trainer");
            StudyGroup group = dataManager.create(StudyGroup.class);
            group.setName("one-save-" + UUID.randomUUID().toString().substring(0, 8));
            group.setOwner(owner);
            group.setStatus(GroupStatus.CREATED);
            group.setAccessMode(GroupAccessMode.ALL_AT_ONCE);
            GroupMember member = dataManager.create(GroupMember.class);
            member.setGroup(group);
            member.setStudent(student());
            GroupCourse one = dataManager.create(GroupCourse.class);
            one.setGroup(group);
            one.setCourse(first);
            one.setSortOrder(1);
            GroupCourse two = dataManager.create(GroupCourse.class);
            two.setGroup(group);
            two.setCourse(second);
            two.setSortOrder(2);
            dataManager.save(new SaveContext().saving(group, member, one, two));
            return group;
        });
        StudyGroup loaded = dataManager.unconstrained().load(StudyGroup.class).id(saved.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("courses", fp2 -> fp2.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE))
                        .add("members", FetchPlan.BASE))
                .one();
        assertThat(loaded.getCourses()).hasSize(2);
        assertThat(loaded.getMembers()).hasSize(1);
        assertThat(sortOrders(loaded)).containsExactly(1, 2);
    }

    @Test
    void lessonSortOrderFromImportAndMove() {
        Course course = course("Уроки по номерам");
        UUID first = publishLesson(course, "lsn-a");
        UUID second = publishLesson(course, "lsn-b");
        UUID third = publishLesson(course, "lsn-c");
        List<Lesson> published = auth.withUser("trainer", () -> lessonProgress.publishedLessons(course));
        assertThat(published.stream().map(Lesson::getId)).containsExactly(first, second, third);
        assertThat(published.stream().map(Lesson::getSortOrder)).containsExactly(1, 2, 3);

        Lesson last = dataManager.unconstrained().load(Lesson.class).id(third).one();
        auth.withUser("trainer", () -> {
            lessonOrder.moveLesson(course, last, -1);
            return null;
        });
        assertThatThrownBy(() -> auth.withUser("trainer2", () -> {
            lessonOrder.moveLesson(course, last, -1);
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        sequentialGroup("seq-lsn-order", course);
        assertThat(auth.withUser("student", () -> lessonProgress.publishedLessons(course)).stream().map(Lesson::getId))
                .containsExactly(first, third, second);

        Integer order = dataManager.unconstrained().load(Lesson.class).id(first).one().getSortOrder();
        auth.withUser("trainer", () -> {
            importService.importPackage(TestPackages.validZip(
                    dataManager.unconstrained().load(Lesson.class).id(first).one().getExternalId(), "v2"),
                    course.getId(), "test");
            return null;
        });
        assertThat(dataManager.unconstrained().load(Lesson.class).id(first).one().getSortOrder()).isEqualTo(order);
    }

    private boolean groupListPermitted() {
        UiShowViewContext context = new UiShowViewContext(PlatformViews.GROUP_LIST);
        accessManager.applyRegisteredConstraints(context);
        return context.isPermitted();
    }

    private boolean groupCreatePermitted() {
        CrudEntityContext context = new CrudEntityContext(metadata.getClass(StudyGroup.class));
        accessManager.applyRegisteredConstraints(context);
        return context.isCreatePermitted();
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

    private List<Integer> sortOrders(StudyGroup group) {
        return groupProgress.orderedCourses(group).stream().map(GroupCourse::getSortOrder).toList();
    }

    private StudyGroup saveGroupTogether(String name, GroupAccessMode mode, List<Course> courses) {
        User owner = user(ownerUsername(courses.get(0)));
        StudyGroup group = dataManager.create(StudyGroup.class);
        group.setName(name + "-" + UUID.randomUUID().toString().substring(0, 8));
        group.setOwner(owner);
        group.setStatus(GroupStatus.CREATED);
        group.setAccessMode(mode);
        SaveContext save = new SaveContext().saving(group);
        int order = 1;
        for (Course course : courses) {
            GroupCourse line = dataManager.create(GroupCourse.class);
            line.setGroup(group);
            line.setCourse(course);
            line.setSortOrder(order++);
            save.saving(line);
        }
        dataManager.save(save);
        return dataManager.load(StudyGroup.class).id(group.getId()).one();
    }

    private User extraStudent() {
        String username = "student-" + UUID.randomUUID().toString().substring(0, 8);
        return auth.withUser("admin", () -> {
            User created = dataManager.create(User.class);
            created.setUsername(username);
            created.setPassword(passwordEncoder.encode(username));
            created.setActive(true);
            created = dataManager.save(created);
            assign(username, StudentRole.CODE, "resource");
            assign(username, UiMinimalRole.CODE, "resource");
            assign(username, StudentPublishedRole.CODE, "row_level");
            return created;
        });
    }

    private void assign(String username, String role, String type) {
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(username);
        assignment.setRoleCode(role);
        assignment.setRoleType(type);
        dataManager.save(assignment);
    }
}
