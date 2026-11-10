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
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

@Component("uic_LessonAccessListener")
public class LessonAccessListener implements BeforeDeleteEntityListener<Lesson> {

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

    @EventListener
    public void onLessonSaving(EntitySavingEvent<Lesson> event) {
        Lesson lesson = event.getEntity();
        boolean isNew = entityStates.isNew(lesson);
        Course newCourse = courseOf(lesson);
        Course oldCourse = isNew || lesson.getId() == null ? null : storedCourse(lesson.getId());
        if (courseChanged(oldCourse, newCourse)) {
            assertCanSave(oldCourse);
            assertCanSave(newCourse);
            return;
        }
        assertCanSave(newCourse);
    }

    @EventListener
    public void onLessonVersionSaving(EntitySavingEvent<LessonVersion> event) {
        LessonVersion version = event.getEntity();
        if (!entityStates.isNew(version) && !entityStates.isLoaded(version, "lesson")) {
            return;
        }
        Lesson lesson = version.getLesson();
        Course course = courseOf(lesson);
        if (course == null && lesson != null && lesson.getId() != null) {
            course = storedCourse(lesson.getId());
        }
        assertCanSave(course);
    }

    @Override
    public void onBeforeDelete(Lesson lesson) {
        assertCanDelete(courseOf(lesson));
    }

    private Course storedCourse(UUID lessonId) {
        Lesson stored = dataManager.unconstrained().load(Lesson.class)
                .id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE))
                .optional()
                .orElse(null);
        return stored == null ? null : stored.getCourse();
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
            return storedCourse(lesson.getId());
        }
        return null;
    }

    private void assertCanSave(Course course) {
        if (access.isAdmin() || access.isReviewer()) {
            return;
        }
        assertCanDelete(course);
    }

    private void assertCanDelete(Course course) {
        if (access.isAdmin()) {
            return;
        }
        if (course == null) {
            if (access.isTrainer()) {
                return;
            }
            throw new AccessDeniedException("entity", "uic_Lesson", "update");
        }
        if (!courseAccess.canEdit(course)) {
            throw new AccessDeniedException("entity", "uic_Lesson", "update");
        }
    }
}
