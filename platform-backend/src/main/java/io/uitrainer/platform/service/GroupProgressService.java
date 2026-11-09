package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseProgressStatus;
import io.uitrainer.platform.entity.GroupAccessMode;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupCourseProgress;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class GroupProgressService {

    private final DataManager dataManager;
    private final TimeSource timeSource;

    public GroupProgressService(DataManager dataManager, TimeSource timeSource) {
        this.dataManager = dataManager;
        this.timeSource = timeSource;
    }

    @Transactional
    public void recalculateForStudentAndCourse(User student, Course course) {
        List<StudyGroup> groups = dataManager.unconstrained().load(StudyGroup.class)
                .query("select distinct g from uic_StudyGroup g, uic_GroupMember m, uic_GroupCourse gc "
                        + "where m.group = g and gc.group = g and m.student = :s and gc.course = :c")
                .parameter("s", student)
                .parameter("c", course)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("courses", FetchPlan.BASE)
                        .add("members", FetchPlan.BASE))
                .list();
        for (StudyGroup group : groups) {
            recalculate(group, student, course);
        }
    }

    @Transactional
    public GroupCourseProgress recalculate(StudyGroup group, User student, Course course) {
        GroupCourseProgress progress = loadOrCreate(group, student, course);
        if (progress.getStatus() == CourseProgressStatus.COMPLETED) {
            return progress;
        }
        long published = publishedLessonCount(course);
        long completed = completedPublishedLessonCount(student, course);
        boolean started = completed > 0 || hasStartedLesson(student, course);
        if (published > 0 && completed >= published) {
            progress.setStatus(CourseProgressStatus.COMPLETED);
            progress.setCompletedAt(now());
            progress.setPublishedLessonCountAtCompletion((int) published);
        } else if (started) {
            progress.setStatus(CourseProgressStatus.STARTED);
            progress.setCompletedAt(null);
        } else {
            progress.setStatus(CourseProgressStatus.NOT_STARTED);
            progress.setCompletedAt(null);
        }
        return dataManager.unconstrained().save(progress);
    }

    public boolean isCourseAccessible(StudyGroup group, User student, Course course) {
        if (group.getStatus() != GroupStatus.ACTIVE) {
            return false;
        }
        if (!isMember(group, student)) {
            return false;
        }
        List<GroupCourse> ordered = orderedCourses(group);
        if (ordered.stream().noneMatch(gc -> gc.getCourse().getId().equals(course.getId()))) {
            return false;
        }
        if (group.getAccessMode() == GroupAccessMode.ALL_AT_ONCE) {
            return true;
        }
        for (GroupCourse gc : ordered) {
            if (gc.getCourse().getId().equals(course.getId())) {
                return true;
            }
            GroupCourseProgress previous = findProgress(group, student, gc.getCourse());
            if (previous == null || previous.getStatus() != CourseProgressStatus.COMPLETED) {
                return false;
            }
        }
        return false;
    }

    public List<GroupCourse> accessibleCourses(StudyGroup group, User student) {
        List<GroupCourse> result = new ArrayList<>();
        for (GroupCourse gc : orderedCourses(group)) {
            if (isCourseAccessible(group, student, gc.getCourse())) {
                result.add(gc);
            }
        }
        return result;
    }

    public CourseProgressStatus status(StudyGroup group, User student, Course course) {
        GroupCourseProgress progress = findProgress(group, student, course);
        return progress == null ? CourseProgressStatus.NOT_STARTED : progress.getStatus();
    }

    public GroupSummary summarizeStudent(StudyGroup group, User student) {
        List<GroupCourse> all = orderedCourses(group);
        long completed = 0;
        for (GroupCourse gc : all) {
            if (status(group, student, gc.getCourse()) == CourseProgressStatus.COMPLETED) {
                completed++;
            }
        }
        return new GroupSummary(group, 1, completed, all.size());
    }

    public GroupSummary summarize(StudyGroup group) {
        StudyGroup loaded = dataManager.unconstrained().load(StudyGroup.class)
                .id(group.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("members", FetchPlan.BASE)
                        .add("courses", FetchPlan.BASE))
                .one();
        long members = loaded.getMembers() == null ? 0 : loaded.getMembers().size();
        long courses = loaded.getCourses() == null ? 0 : loaded.getCourses().size();
        long totalPairs = members * courses;
        Long completed = dataManager.unconstrained().loadValue(
                        "select count(p) from uic_GroupCourseProgress p where p.group.id = :g and p.status = :st",
                        Long.class)
                .parameter("g", loaded.getId())
                .parameter("st", CourseProgressStatus.COMPLETED.getId())
                .one();
        return new GroupSummary(loaded, members, completed == null ? 0 : completed, totalPairs);
    }

    public List<GroupCourse> orderedCourses(StudyGroup group) {
        List<GroupCourse> courses = dataManager.unconstrained().load(GroupCourse.class)
                .query("select gc from uic_GroupCourse gc where gc.group.id = :g order by gc.sortOrder, gc.course.title")
                .parameter("g", group.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("course", FetchPlan.BASE)
                        .add("group", FetchPlan.BASE))
                .list();
        courses.sort(Comparator.comparing(GroupCourse::getSortOrder, Comparator.nullsLast(Integer::compareTo)));
        return courses;
    }

    private GroupCourseProgress loadOrCreate(StudyGroup group, User student, Course course) {
        GroupCourseProgress existing = findProgress(group, student, course);
        if (existing != null) {
            return existing;
        }
        GroupCourseProgress created = dataManager.unconstrained().create(GroupCourseProgress.class);
        created.setGroup(group);
        created.setStudent(student);
        created.setCourse(course);
        created.setStatus(CourseProgressStatus.NOT_STARTED);
        return created;
    }

    private GroupCourseProgress findProgress(StudyGroup group, User student, Course course) {
        return dataManager.unconstrained().load(GroupCourseProgress.class)
                .query("select p from uic_GroupCourseProgress p where p.group.id = :g and p.student.id = :s and p.course.id = :c")
                .parameter("g", group.getId())
                .parameter("s", student.getId())
                .parameter("c", course.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("group", FetchPlan.INSTANCE_NAME)
                        .add("student", FetchPlan.INSTANCE_NAME)
                        .add("course", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(null);
    }

    private boolean isMember(StudyGroup group, User student) {
        Long count = dataManager.unconstrained().loadValue(
                        "select count(m) from uic_GroupMember m where m.group.id = :g and m.student.id = :s",
                        Long.class)
                .parameter("g", group.getId())
                .parameter("s", student.getId())
                .one();
        return count != null && count > 0;
    }

    private long publishedLessonCount(Course course) {
        Long count = dataManager.unconstrained().loadValue(
                        "select count(l) from uic_Lesson l where l.course.id = :c and l.status = :st",
                        Long.class)
                .parameter("c", course.getId())
                .parameter("st", LessonStatus.PUBLISHED.getId())
                .one();
        return count == null ? 0 : count;
    }

    private long completedPublishedLessonCount(User student, Course course) {
        Long count = dataManager.unconstrained().loadValue(
                        "select count(a) from uic_LessonAttempt a where a.student.id = :s "
                                + "and a.completedAt is not null and a.lesson.course.id = :c and a.lesson.status = :st",
                        Long.class)
                .parameter("s", student.getId())
                .parameter("c", course.getId())
                .parameter("st", LessonStatus.PUBLISHED.getId())
                .one();
        return count == null ? 0 : count;
    }

    private boolean hasStartedLesson(User student, Course course) {
        Long count = dataManager.unconstrained().loadValue(
                        "select count(a) from uic_LessonAttempt a where a.student.id = :s and a.lesson.course.id = :c",
                        Long.class)
                .parameter("s", student.getId())
                .parameter("c", course.getId())
                .one();
        return count != null && count > 0;
    }

    private OffsetDateTime now() {
        return timeSource.now().toOffsetDateTime();
    }

    public record GroupSummary(StudyGroup group, long members, long completedPairs, long totalPairs) {
        public String progressLabel() {
            if (totalPairs == 0) {
                return "нет курсов";
            }
            return completedPairs + " / " + totalPairs + " пройдено";
        }
    }

    public StudyGroup loadGroup(UUID id) {
        return dataManager.load(StudyGroup.class)
                .id(id)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("owner", FetchPlan.INSTANCE_NAME)
                        .add("courses", fp2 -> fp2.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE))
                        .add("members", fp2 -> fp2.addFetchPlan(FetchPlan.BASE).add("student", FetchPlan.INSTANCE_NAME)))
                .one();
    }
}
