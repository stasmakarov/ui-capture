package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.core.security.AccessDeniedException;
import io.jmix.data.listener.BeforeDeleteEntityListener;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.User;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component("uic_LessonAccessListener")
public class LessonAccessListener implements BeforeDeleteEntityListener<Lesson> {

    private static final ThreadLocal<Boolean> LIFECYCLE_SAVE = new ThreadLocal<>();

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final CourseAccessService courseAccess;
    private final CurrentUserAccess access;

    public LessonAccessListener(DataManager dataManager, EntityStates entityStates,
                                CourseAccessService courseAccess, CurrentUserAccess access) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.courseAccess = courseAccess;
        this.access = access;
    }

    static void runLifecycleSave(Runnable action) {
        LIFECYCLE_SAVE.set(Boolean.TRUE);
        try {
            action.run();
        } finally {
            LIFECYCLE_SAVE.remove();
        }
    }

    @EventListener
    public void onLessonSaving(EntitySavingEvent<Lesson> event) {
        Lesson lesson = event.getEntity();
        boolean isNew = entityStates.isNew(lesson);
        if (isNew) {
            assertCanSave(lesson, courseOf(lesson), true);
            return;
        }
        Lesson stored = lesson.getId() == null ? null : storedLesson(lesson.getId());
        if (stored == null) {
            throw new AccessDeniedException("entity", "uic_Lesson", "update");
        }
        assertCanSave(stored, stored.getCourse(), false);
        Course newCourse = courseOf(lesson);
        if (courseChanged(stored.getCourse(), newCourse) && newCourse != null) {
            assertCanSave(stored, newCourse, false);
        }
    }

    @EventListener
    public void onLessonVersionSaving(EntitySavingEvent<LessonVersion> event) {
        LessonVersion version = event.getEntity();
        boolean isNew = entityStates.isNew(version);
        Lesson newLesson = isNew || entityStates.isLoaded(version, "lesson") ? version.getLesson() : null;
        Lesson oldLesson = isNew || version.getId() == null ? null : storedVersionLesson(version.getId());
        if (oldLesson != null) {
            assertCanSave(oldLesson, courseOf(oldLesson), false);
        }
        if (newLesson != null && (oldLesson == null || !Objects.equals(oldLesson.getId(), newLesson.getId()))) {
            assertCanSave(newLesson, courseOf(newLesson), entityStates.isNew(newLesson));
        }
        if (oldLesson == null && newLesson == null) {
            throw new AccessDeniedException("entity", "uic_LessonVersion", "update");
        }
    }

    @Override
    public void onBeforeDelete(Lesson lesson) {
        if (access.isAdmin()) {
            return;
        }
        if (entityStates.isLoaded(lesson, "course") && entityStates.isLoaded(lesson, "author")) {
            assertCanMutate(lesson, lesson.getCourse(), false);
            return;
        }
        if (lesson.getId() == null) {
            throw new AccessDeniedException("entity", "uic_Lesson", "delete");
        }
        UUID courseId = dataManager.unconstrained().loadValue(
                        "select l.course.id from uic_Lesson l where l.id = :id", UUID.class)
                .parameter("id", lesson.getId())
                .optional()
                .orElse(null);
        if (courseId == null) {
            String author = dataManager.unconstrained().loadValue(
                            "select a.username from uic_Lesson l join l.author a where l.id = :id", String.class)
                    .parameter("id", lesson.getId())
                    .optional()
                    .orElse(null);
            if (access.isTrainer() && author != null && access.username().equals(author)) {
                return;
            }
            throw new AccessDeniedException("entity", "uic_Lesson", "delete");
        }
        Course course = dataManager.unconstrained().load(Course.class)
                .id(courseId)
                .fetchPlan(FetchPlan.BASE)
                .optional()
                .orElse(null);
        if (!courseAccess.canEdit(course)) {
            throw new AccessDeniedException("entity", "uic_Lesson", "delete");
        }
    }

    private Lesson storedLesson(UUID lessonId) {
        return dataManager.unconstrained().load(Lesson.class)
                .id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("course", FetchPlan.BASE)
                        .add("author", FetchPlan.BASE))
                .optional()
                .orElse(null);
    }

    private Lesson storedVersionLesson(UUID versionId) {
        LessonVersion stored = dataManager.unconstrained().load(LessonVersion.class)
                .id(versionId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("lesson", b -> b.addFetchPlan(FetchPlan.BASE)
                                .add("course", FetchPlan.BASE)
                                .add("author", FetchPlan.BASE)))
                .optional()
                .orElse(null);
        return stored == null ? null : stored.getLesson();
    }

    private boolean courseChanged(Course oldCourse, Course newCourse) {
        UUID oldId = oldCourse == null ? null : oldCourse.getId();
        UUID newId = newCourse == null ? null : newCourse.getId();
        return !Objects.equals(oldId, newId);
    }

    private Course courseOf(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        if (entityStates.isNew(lesson) || entityStates.isLoaded(lesson, "course")) {
            return lesson.getCourse();
        }
        if (lesson.getId() != null) {
            Lesson stored = storedLesson(lesson.getId());
            return stored == null ? null : stored.getCourse();
        }
        return null;
    }

    private void assertCanSave(Lesson lesson, Course course, boolean isNew) {
        if (access.isAdmin()) {
            return;
        }
        if (Boolean.TRUE.equals(LIFECYCLE_SAVE.get()) && access.isReviewer()) {
            return;
        }
        assertCanMutate(lesson, course, isNew);
    }

    private void assertCanMutate(Lesson lesson, Course course, boolean isNew) {
        if (access.isAdmin()) {
            return;
        }
        if (course == null) {
            if (isNew) {
                if (access.isTrainer()) {
                    return;
                }
                throw new AccessDeniedException("entity", "uic_Lesson", "update");
            }
            User author = authorOf(lesson);
            if (access.isTrainer() && author != null && access.username().equals(author.getUsername())) {
                return;
            }
            throw new AccessDeniedException("entity", "uic_Lesson", "update");
        }
        if (!courseAccess.canEdit(course)) {
            throw new AccessDeniedException("entity", "uic_Lesson", "update");
        }
    }

    private User authorOf(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        if (entityStates.isNew(lesson) || entityStates.isLoaded(lesson, "author")) {
            return lesson.getAuthor();
        }
        if (lesson.getId() != null) {
            Lesson stored = storedLesson(lesson.getId());
            return stored == null ? null : stored.getAuthor();
        }
        return null;
    }
}
