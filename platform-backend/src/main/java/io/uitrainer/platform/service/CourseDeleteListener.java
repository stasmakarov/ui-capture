package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.DeletePolicyException;
import io.jmix.core.EntityStates;
import io.jmix.data.listener.BeforeDeleteEntityListener;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.User;
import org.springframework.stereotype.Component;

@Component("uic_CourseDeleteListener")
public class CourseDeleteListener implements BeforeDeleteEntityListener<Course> {

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final CurrentUserAccess access;

    public CourseDeleteListener(DataManager dataManager, EntityStates entityStates, CurrentUserAccess access) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.access = access;
    }

    @Override
    public void onBeforeDelete(Course course) {
        if (course.getId() == null) {
            return;
        }
        if (!mayDelete(course)) {
            throw new io.jmix.core.security.AccessDeniedException("entity", "uic_Course", "delete");
        }
        Long lessons = dataManager.unconstrained().loadValue(
                        "select count(l) from uic_Lesson l where l.course.id = :c", Long.class)
                .parameter("c", course.getId())
                .one();
        if (lessons != null && lessons > 0) {
            throw new DeletePolicyException("uic_Course", "uic_Lesson");
        }
        Long assigned = dataManager.unconstrained().loadValue(
                        "select count(gc) from uic_GroupCourse gc where gc.course.id = :c", Long.class)
                .parameter("c", course.getId())
                .one();
        if (assigned != null && assigned > 0) {
            throw new DeletePolicyException("uic_Course", "uic_GroupCourse");
        }
    }

    private boolean mayDelete(Course course) {
        if (access.isAdmin()) {
            return true;
        }
        if (entityStates.isLoaded(course, "owner")) {
            User owner = course.getOwner();
            if (owner != null && access.username().equals(owner.getUsername())) {
                return true;
            }
        }
        return course.getCreatedBy() != null && course.getCreatedBy().equals(access.username());
    }
}
