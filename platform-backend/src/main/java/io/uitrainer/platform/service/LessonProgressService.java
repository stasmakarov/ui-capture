package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonAttempt;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LessonProgressService {

    private final DataManager dataManager;
    private final TimeSource timeSource;
    private final CurrentUserAccess access;
    private final GroupProgressService groupProgress;

    public LessonProgressService(DataManager dataManager, TimeSource timeSource,
                                 CurrentUserAccess access, GroupProgressService groupProgress) {
        this.dataManager = dataManager;
        this.timeSource = timeSource;
        this.access = access;
        this.groupProgress = groupProgress;
    }

    @Transactional
    public LessonAttempt startLesson(UUID lessonId) {
        User student = currentStudent();
        Lesson lesson = loadLesson(lessonId);
        assertAccessible(student, lesson);
        LessonAttempt existing = findAttempt(student, lesson);
        if (existing != null) {
            return existing;
        }
        LessonVersion version = lesson.getPublishedVersion();
        if (version == null) {
            throw new ConflictException("У урока нет опубликованной версии");
        }
        LessonAttempt attempt = dataManager.create(LessonAttempt.class);
        attempt.setStudent(student);
        attempt.setLesson(lesson);
        attempt.setLessonVersion(version);
        attempt.setStartedAt(timeSource.now().toOffsetDateTime());
        LessonAttempt saved = dataManager.save(attempt);
        groupProgress.recalculateForStudentAndCourse(student, lesson.getCourse());
        return saved;
    }

    @Transactional
    public LessonAttempt completeLesson(UUID lessonId) {
        User student = currentStudent();
        Lesson lesson = loadLesson(lessonId);
        assertAccessible(student, lesson);
        LessonAttempt attempt = findAttempt(student, lesson);
        if (attempt == null) {
            attempt = startLesson(lessonId);
        }
        if (attempt.getCompletedAt() == null) {
            attempt.setCompletedAt(timeSource.now().toOffsetDateTime());
            attempt = dataManager.save(attempt);
        }
        groupProgress.recalculateForStudentAndCourse(student, lesson.getCourse());
        return attempt;
    }

    public LessonAttempt findAttempt(User student, Lesson lesson) {
        return dataManager.load(LessonAttempt.class)
                .query("select a from uic_LessonAttempt a where a.student.id = :s and a.lesson.id = :l")
                .parameter("s", student.getId())
                .parameter("l", lesson.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("lesson", FetchPlan.BASE)
                        .add("lessonVersion", FetchPlan.INSTANCE_NAME)
                        .add("student", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(null);
    }

    private void assertAccessible(User student, Lesson lesson) {
        Course course = lesson.getCourse();
        if (course == null || lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new ConflictException("Урок недоступен");
        }
        List<StudyGroup> groups = dataManager.load(StudyGroup.class)
                .query("select g from uic_StudyGroup g join g.members m join g.courses gc "
                        + "where m.student.id = :s and gc.course.id = :c and g.status = :st")
                .parameter("s", student.getId())
                .parameter("c", course.getId())
                .parameter("st", GroupStatus.ACTIVE.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("courses", fp2 -> fp2.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.INSTANCE_NAME)))
                .list();
        for (StudyGroup group : groups) {
            if (groupProgress.isCourseAccessible(group, student, course)) {
                return;
            }
        }
        throw new ConflictException("Курс пока закрыт");
    }

    private Lesson loadLesson(UUID lessonId) {
        return dataManager.load(Lesson.class)
                .id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("course", FetchPlan.BASE)
                        .add("publishedVersion", FetchPlan.BASE))
                .one();
    }

    private User currentStudent() {
        User user = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .one();
        if (!access.isStudent() && !access.isAdmin()) {
            throw new ConflictException("Пройти урок может только ученик");
        }
        return user;
    }

    public List<Lesson> publishedLessons(Course course) {
        return dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.course.id = :c and l.status = :st order by l.createdDate")
                .parameter("c", course.getId())
                .parameter("st", LessonStatus.PUBLISHED.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("publishedVersion", FetchPlan.BASE)
                        .add("course", FetchPlan.INSTANCE_NAME))
                .list();
    }
}
