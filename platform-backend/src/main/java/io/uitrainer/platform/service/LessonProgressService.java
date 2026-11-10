package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonAttempt;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.User;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

@Service
public class LessonProgressService {

    private static final String ATTEMPT_UNIQUE = "UQ_UIC_LESSON_ATTEMPT";

    private final DataManager dataManager;
    private final TimeSource timeSource;
    private final CurrentUserAccess access;
    private final GroupProgressService groupProgress;
    private final LessonAttemptInsert attemptInsert;

    public LessonProgressService(DataManager dataManager, TimeSource timeSource,
                                 CurrentUserAccess access, GroupProgressService groupProgress,
                                 LessonAttemptInsert attemptInsert) {
        this.dataManager = dataManager;
        this.timeSource = timeSource;
        this.access = access;
        this.groupProgress = groupProgress;
        this.attemptInsert = attemptInsert;
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
        LessonAttempt saved;
        try {
            saved = attemptInsert.insert(attempt);
        } catch (DataIntegrityViolationException | PersistenceException ex) {
            if (!isAttemptUniqueViolation(ex)) {
                throw ex;
            }
            LessonAttempt raced = findAttempt(student, lesson);
            if (raced != null) {
                return raced;
            }
            throw ex;
        }
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

    public List<LessonAttempt> attemptsFor(User student, List<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return List.of();
        }
        return dataManager.load(LessonAttempt.class)
                .query("select a from uic_LessonAttempt a where a.student.id = :s and a.lesson.id in :ids")
                .parameter("s", student.getId())
                .parameter("ids", lessons.stream().map(Lesson::getId).toList())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("lesson", FetchPlan.INSTANCE_NAME)
                        .add("lessonVersion", FetchPlan.INSTANCE_NAME)
                        .add("student", FetchPlan.INSTANCE_NAME))
                .list();
    }

    private void assertAccessible(User student, Lesson lesson) {
        Course course = lesson.getCourse();
        if (course == null || lesson.getStatus() != LessonStatus.PUBLISHED) {
            throw new ConflictException("Урок недоступен");
        }
        if (!groupProgress.canStudentOpenLesson(student, lesson)) {
            throw new ConflictException("Курс пока закрыт");
        }
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

    private boolean isAttemptUniqueViolation(Throwable ex) {
        while (ex != null) {
            if (ex instanceof SQLException sql && "23505".equals(sql.getSQLState())) {
                return true;
            }
            String message = ex.getMessage();
            if (message != null && message.toUpperCase().contains(ATTEMPT_UNIQUE)) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    public List<Lesson> publishedLessons(Course course) {
        return dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.course.id = :c and l.status = :st order by l.sortOrder, l.createdDate")
                .parameter("c", course.getId())
                .parameter("st", LessonStatus.PUBLISHED.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("publishedVersion", FetchPlan.BASE)
                        .add("course", FetchPlan.INSTANCE_NAME))
                .list();
    }
}
