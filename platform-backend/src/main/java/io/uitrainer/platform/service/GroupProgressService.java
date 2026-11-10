package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.jmix.core.entity.KeyValueEntity;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseProgressStatus;
import io.uitrainer.platform.entity.GroupAccessMode;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupCourseProgress;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
                .fetchPlan(FetchPlan.BASE)
                .list();
        for (StudyGroup group : groups) {
            recalculate(group, student, course);
        }
    }

    @Transactional
    public void recalculateStudentInGroup(StudyGroup group, User student) {
        List<GroupCourse> ordered = orderedCourses(group);
        List<UUID> courseIds = ordered.stream().map(gc -> gc.getCourse().getId()).toList();
        Map<UUID, Long> published = publishedCounts(courseIds);
        Map<UUID, Long> completed = completedCounts(student.getId(), courseIds);
        Set<UUID> started = startedCourses(student.getId(), courseIds);
        Map<UUID, GroupCourseProgress> stored = progressByCourse(group.getId(), student.getId());
        SaveContext save = new SaveContext();
        boolean dirty = false;
        for (GroupCourse gc : ordered) {
            Course course = gc.getCourse();
            GroupCourseProgress progress = stored.get(course.getId());
            boolean created = progress == null;
            if (created) {
                progress = newProgress(group, student, course);
            }
            if (!created && progress.getStatus() == CourseProgressStatus.COMPLETED) {
                continue;
            }
            CourseProgressStatus oldStatus = progress.getStatus();
            OffsetDateTime oldCompletedAt = progress.getCompletedAt();
            Integer oldCount = progress.getPublishedLessonCountAtCompletion();
            applyStatus(progress,
                    published.getOrDefault(course.getId(), 0L),
                    completed.getOrDefault(course.getId(), 0L),
                    started.contains(course.getId()) || completed.getOrDefault(course.getId(), 0L) > 0);
            if (created
                    || oldStatus != progress.getStatus()
                    || !Objects.equals(oldCompletedAt, progress.getCompletedAt())
                    || !Objects.equals(oldCount, progress.getPublishedLessonCountAtCompletion())) {
                save.saving(progress);
                dirty = true;
            }
        }
        if (dirty) {
            dataManager.unconstrained().save(save);
        }
    }

    @Transactional
    public GroupCourseProgress recalculate(StudyGroup group, User student, Course course) {
        GroupCourseProgress progress = loadOrCreate(group, student, course);
        if (progress.getStatus() == CourseProgressStatus.COMPLETED) {
            return progress;
        }
        long published = publishedLessonCount(course.getId());
        long completed = completedPublishedLessonCount(student.getId(), course.getId());
        boolean started = completed > 0 || hasStartedLesson(student.getId(), course.getId());
        applyStatus(progress, published, completed, started);
        return dataManager.unconstrained().save(progress);
    }

    public boolean isCourseAccessible(StudyGroup group, User student, Course course) {
        return snapshot(group, student).accessible().stream()
                .anyMatch(gc -> gc.getCourse().getId().equals(course.getId()));
    }

    public List<GroupCourse> accessibleCourses(StudyGroup group, User student) {
        return snapshot(group, student).accessible();
    }

    public CourseProgressStatus status(StudyGroup group, User student, Course course) {
        return snapshot(group, student).status(course.getId());
    }

    public boolean canStudentOpenLesson(User student, Lesson lesson) {
        if (student == null || lesson == null || lesson.getCourse() == null) {
            return false;
        }
        Course course = lesson.getCourse();
        List<StudyGroup> groups = dataManager.unconstrained().load(StudyGroup.class)
                .query("select distinct g from uic_StudyGroup g, uic_GroupMember m, uic_GroupCourse gc "
                        + "where m.group = g and gc.group = g and m.student.id = :s and gc.course.id = :c and g.status = :st")
                .parameter("s", student.getId())
                .parameter("c", course.getId())
                .parameter("st", GroupStatus.ACTIVE.getId())
                .fetchPlan(FetchPlan.BASE)
                .list();
        for (StudyGroup group : groups) {
            if (isCourseAccessible(group, student, course)) {
                return true;
            }
        }
        return false;
    }

    public GroupSnapshot snapshot(StudyGroup group, User student) {
        StudyGroup loaded = dataManager.unconstrained().load(StudyGroup.class)
                .id(group.getId())
                .fetchPlan(FetchPlan.BASE)
                .one();
        boolean member = isMember(loaded, student);
        List<GroupCourse> ordered = orderedCourses(loaded);
        List<UUID> courseIds = ordered.stream().map(gc -> gc.getCourse().getId()).toList();
        Map<UUID, Long> published = publishedCounts(courseIds);
        Map<UUID, Long> completedAttempts = completedCounts(student.getId(), courseIds);
        Set<UUID> started = startedCourses(student.getId(), courseIds);
        Map<UUID, CourseProgressStatus> stored = storedStatuses(loaded.getId(), student.getId());
        Map<UUID, CourseProgressStatus> statuses = new HashMap<>();
        for (GroupCourse gc : ordered) {
            UUID courseId = gc.getCourse().getId();
            statuses.put(courseId, resolveStatus(stored.get(courseId),
                    published.getOrDefault(courseId, 0L),
                    completedAttempts.getOrDefault(courseId, 0L),
                    started.contains(courseId)));
        }
        return new GroupSnapshot(loaded, member, ordered, published, statuses);
    }

    public GroupSummary summarizeStudent(StudyGroup group, User student) {
        GroupSnapshot snap = snapshot(group, student);
        long total = 0;
        long completed = 0;
        for (GroupCourse gc : snap.ordered()) {
            if (snap.published(gc.getCourse().getId()) == 0) {
                continue;
            }
            total++;
            if (snap.status(gc.getCourse().getId()) == CourseProgressStatus.COMPLETED) {
                completed++;
            }
        }
        return new GroupSummary(snap.group(), 1, completed, total);
    }

    public GroupSummary summarize(StudyGroup group) {
        return summarize(List.of(group)).get(group.getId());
    }

    public Map<UUID, GroupSummary> summarize(Collection<StudyGroup> groups) {
        Map<UUID, GroupSummary> result = new java.util.LinkedHashMap<>();
        if (groups == null || groups.isEmpty()) {
            return result;
        }
        List<StudyGroup> list = groups.stream().filter(g -> g.getId() != null).toList();
        if (list.isEmpty()) {
            return result;
        }
        List<UUID> gids = list.stream().map(StudyGroup::getId).toList();
        Map<UUID, Long> memberCounts = new HashMap<>();
        for (KeyValueEntity row : dataManager.unconstrained().loadValues(
                        "select m.group.id, count(m) from uic_GroupMember m where m.group.id in :ids group by m.group.id")
                .properties("groupId", "cnt")
                .parameter("ids", gids)
                .list()) {
            memberCounts.put(row.getValue("groupId"), ((Number) row.getValue("cnt")).longValue());
        }
        Map<UUID, List<UUID>> coursesByGroup = new HashMap<>();
        Set<UUID> allCourseIds = new HashSet<>();
        List<GroupCourse> lines = dataManager.unconstrained().load(GroupCourse.class)
                .query("select gc from uic_GroupCourse gc where gc.group.id in :ids")
                .parameter("ids", gids)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE).add("group", FetchPlan.BASE))
                .list();
        for (GroupCourse line : lines) {
            UUID gid = line.getGroup().getId();
            UUID cid = line.getCourse().getId();
            coursesByGroup.computeIfAbsent(gid, k -> new ArrayList<>()).add(cid);
            allCourseIds.add(cid);
        }
        Map<UUID, Long> published = publishedCounts(List.copyOf(allCourseIds));
        Map<String, Long> completedByGroupCourse = new HashMap<>();
        if (!gids.isEmpty() && !allCourseIds.isEmpty()) {
            for (KeyValueEntity row : dataManager.unconstrained().loadValues(
                            "select p.group.id, p.course.id, count(p) from uic_GroupCourseProgress p "
                                    + "where p.group.id in :gids and p.status = :st "
                                    + "and p.student.id in (select m.student.id from uic_GroupMember m where m.group.id = p.group.id) "
                                    + "group by p.group.id, p.course.id")
                    .properties("groupId", "courseId", "cnt")
                    .parameter("gids", gids)
                    .parameter("st", CourseProgressStatus.COMPLETED.getId())
                    .list()) {
                completedByGroupCourse.put(row.getValue("groupId") + ":" + row.getValue("courseId"),
                        ((Number) row.getValue("cnt")).longValue());
            }
        }
        for (StudyGroup group : list) {
            List<UUID> courseIds = coursesByGroup.getOrDefault(group.getId(), List.of());
            List<UUID> contentIds = courseIds.stream().filter(id -> published.getOrDefault(id, 0L) > 0).toList();
            long members = memberCounts.getOrDefault(group.getId(), 0L);
            long totalPairs = members * contentIds.size();
            long completedPairs = 0;
            for (UUID courseId : contentIds) {
                completedPairs += completedByGroupCourse.getOrDefault(group.getId() + ":" + courseId, 0L);
            }
            completedPairs = Math.min(completedPairs, totalPairs);
            result.put(group.getId(), new GroupSummary(group, members, completedPairs, totalPairs));
        }
        return result;
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

    @Transactional
    public void normalizeCourseOrder(UUID groupId) {
        if (groupId == null) {
            return;
        }
        StudyGroup group = dataManager.unconstrained().load(StudyGroup.class).id(groupId).one();
        List<GroupCourse> ordered = orderedCourses(group);
        SortOrders.normalize(ordered, GroupCourse::setSortOrder);
        if (!ordered.isEmpty()) {
            dataManager.unconstrained().save(new SaveContext().saving(ordered.toArray()));
        }
    }

    public int nextSortOrder(StudyGroup group) {
        int max = 0;
        if (group.getId() != null) {
            Integer stored = dataManager.unconstrained().loadValue(
                            "select max(gc.sortOrder) from uic_GroupCourse gc where gc.group.id = :g", Integer.class)
                    .parameter("g", group.getId())
                    .optional()
                    .orElse(null);
            if (stored != null) {
                max = stored;
            }
        }
        if (group.getCourses() != null) {
            for (GroupCourse gc : group.getCourses()) {
                if (gc.getSortOrder() != null) {
                    max = Math.max(max, gc.getSortOrder());
                }
            }
        }
        return max + 1;
    }

    @Transactional
    public void deleteProgressForMember(UUID groupId, UUID studentId) {
        List<GroupCourseProgress> rows = dataManager.unconstrained().load(GroupCourseProgress.class)
                .query("select p from uic_GroupCourseProgress p where p.group.id = :g and p.student.id = :s")
                .parameter("g", groupId)
                .parameter("s", studentId)
                .list();
        if (!rows.isEmpty()) {
            dataManager.unconstrained().remove(rows.toArray());
        }
    }

    @Transactional
    public void deleteProgressForCourse(UUID groupId, UUID courseId) {
        List<GroupCourseProgress> rows = dataManager.unconstrained().load(GroupCourseProgress.class)
                .query("select p from uic_GroupCourseProgress p where p.group.id = :g and p.course.id = :c")
                .parameter("g", groupId)
                .parameter("c", courseId)
                .list();
        if (!rows.isEmpty()) {
            dataManager.unconstrained().remove(rows.toArray());
        }
    }

    private CourseProgressStatus resolveStatus(CourseProgressStatus stored, long published, long completed, boolean started) {
        if (stored == CourseProgressStatus.COMPLETED) {
            return CourseProgressStatus.COMPLETED;
        }
        if (published > 0 && completed >= published) {
            return CourseProgressStatus.COMPLETED;
        }
        if (started || completed > 0) {
            return CourseProgressStatus.STARTED;
        }
        return CourseProgressStatus.NOT_STARTED;
    }

    private void applyStatus(GroupCourseProgress progress, long published, long completed, boolean started) {
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
    }

    private GroupCourseProgress loadOrCreate(StudyGroup group, User student, Course course) {
        GroupCourseProgress existing = findProgress(group, student, course);
        if (existing != null) {
            return existing;
        }
        return newProgress(group, student, course);
    }

    private GroupCourseProgress newProgress(StudyGroup group, User student, Course course) {
        GroupCourseProgress created = dataManager.unconstrained().create(GroupCourseProgress.class);
        created.setGroup(group);
        created.setStudent(student);
        created.setCourse(course);
        created.setStatus(CourseProgressStatus.NOT_STARTED);
        return created;
    }

    private Map<UUID, GroupCourseProgress> progressByCourse(UUID groupId, UUID studentId) {
        Map<UUID, GroupCourseProgress> result = new HashMap<>();
        List<GroupCourseProgress> rows = dataManager.unconstrained().load(GroupCourseProgress.class)
                .query("select p from uic_GroupCourseProgress p where p.group.id = :g and p.student.id = :s")
                .parameter("g", groupId)
                .parameter("s", studentId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("course", FetchPlan.INSTANCE_NAME)
                        .add("group", FetchPlan.INSTANCE_NAME)
                        .add("student", FetchPlan.INSTANCE_NAME))
                .list();
        for (GroupCourseProgress row : rows) {
            result.put(row.getCourse().getId(), row);
        }
        return result;
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

    private long publishedLessonCount(UUID courseId) {
        return publishedCounts(List.of(courseId)).getOrDefault(courseId, 0L);
    }

    private Map<UUID, Long> publishedCounts(List<UUID> courseIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (courseIds.isEmpty()) {
            return result;
        }
        List<KeyValueEntity> rows = dataManager.unconstrained().loadValues(
                        "select l.course.id, count(l) from uic_Lesson l where l.course.id in :ids and l.status = :st group by l.course.id")
                .properties("courseId", "cnt")
                .parameter("ids", courseIds)
                .parameter("st", LessonStatus.PUBLISHED.getId())
                .list();
        for (KeyValueEntity row : rows) {
            result.put(row.getValue("courseId"), ((Number) row.getValue("cnt")).longValue());
        }
        return result;
    }

    private long completedPublishedLessonCount(UUID studentId, UUID courseId) {
        return completedCounts(studentId, List.of(courseId)).getOrDefault(courseId, 0L);
    }

    private Map<UUID, Long> completedCounts(UUID studentId, List<UUID> courseIds) {
        Map<UUID, Long> result = new HashMap<>();
        if (courseIds.isEmpty()) {
            return result;
        }
        List<KeyValueEntity> rows = dataManager.unconstrained().loadValues(
                        "select a.lesson.course.id, count(a) from uic_LessonAttempt a where a.student.id = :s "
                                + "and a.completedAt is not null and a.lesson.course.id in :ids and a.lesson.status = :st "
                                + "group by a.lesson.course.id")
                .properties("courseId", "cnt")
                .parameter("s", studentId)
                .parameter("ids", courseIds)
                .parameter("st", LessonStatus.PUBLISHED.getId())
                .list();
        for (KeyValueEntity row : rows) {
            result.put(row.getValue("courseId"), ((Number) row.getValue("cnt")).longValue());
        }
        return result;
    }

    private boolean hasStartedLesson(UUID studentId, UUID courseId) {
        return startedCourses(studentId, List.of(courseId)).contains(courseId);
    }

    private Set<UUID> startedCourses(UUID studentId, List<UUID> courseIds) {
        Set<UUID> result = new HashSet<>();
        if (courseIds.isEmpty()) {
            return result;
        }
        List<KeyValueEntity> rows = dataManager.unconstrained().loadValues(
                        "select distinct a.lesson.course.id from uic_LessonAttempt a where a.student.id = :s and a.lesson.course.id in :ids")
                .properties("courseId")
                .parameter("s", studentId)
                .parameter("ids", courseIds)
                .list();
        for (KeyValueEntity row : rows) {
            result.add(row.getValue("courseId"));
        }
        return result;
    }

    private Map<UUID, CourseProgressStatus> storedStatuses(UUID groupId, UUID studentId) {
        Map<UUID, CourseProgressStatus> result = new HashMap<>();
        List<GroupCourseProgress> rows = dataManager.unconstrained().load(GroupCourseProgress.class)
                .query("select p from uic_GroupCourseProgress p where p.group.id = :g and p.student.id = :s")
                .parameter("g", groupId)
                .parameter("s", studentId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.INSTANCE_NAME))
                .list();
        for (GroupCourseProgress row : rows) {
            result.put(row.getCourse().getId(), row.getStatus());
        }
        return result;
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

    public record GroupSnapshot(StudyGroup group, boolean member, List<GroupCourse> ordered,
                               Map<UUID, Long> published, Map<UUID, CourseProgressStatus> statuses) {
        public long published(UUID courseId) {
            return published.getOrDefault(courseId, 0L);
        }

        public CourseProgressStatus status(UUID courseId) {
            return statuses.getOrDefault(courseId, CourseProgressStatus.NOT_STARTED);
        }

        public List<GroupCourse> accessible() {
            if (!member || group.getStatus() != GroupStatus.ACTIVE) {
                return List.of();
            }
            List<GroupCourse> result = new ArrayList<>();
            boolean sequential = group.getAccessMode() == GroupAccessMode.SEQUENTIAL;
            boolean blocked = false;
            for (GroupCourse gc : ordered) {
                UUID courseId = gc.getCourse().getId();
                if (published(courseId) == 0) {
                    continue;
                }
                if (sequential && blocked) {
                    continue;
                }
                result.add(gc);
                if (sequential && status(courseId) != CourseProgressStatus.COMPLETED) {
                    blocked = true;
                }
            }
            return result;
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
