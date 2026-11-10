package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.DeletePolicyException;
import io.jmix.core.EntityStates;
import io.jmix.data.listener.BeforeDeleteEntityListener;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("uic_CourseDeleteListener")
public class CourseDeleteListener implements BeforeDeleteEntityListener<Course> {

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final CurrentUserAccess access;
    private final JdbcTemplate jdbc;

    public CourseDeleteListener(DataManager dataManager, EntityStates entityStates,
                                CurrentUserAccess access, JdbcTemplate jdbc) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.access = access;
        this.jdbc = jdbc;
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
        jdbc.update("delete from UIC_COURSE_COAUTHOR where COURSE_ID = ?", course.getId());
        jdbc.update("delete from UIC_COURSE_SHARED_WITH where COURSE_ID = ?", course.getId());
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
        String ownerUsername = dataManager.unconstrained().loadValue(
                        "select o.username from uic_Course c join c.owner o where c.id = :id", String.class)
                .parameter("id", course.getId())
                .optional()
                .orElse(null);
        return ownerUsername != null && ownerUsername.equals(access.username());
    }
}
