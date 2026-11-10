package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.core.security.AccessDeniedException;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseCoauthor;
import io.uitrainer.platform.entity.CourseSharedWith;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CourseAccessListener {

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final CourseAccessService courseAccess;

    public CourseAccessListener(DataManager dataManager, EntityStates entityStates, CourseAccessService courseAccess) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.courseAccess = courseAccess;
    }

    @EventListener
    public void onCourseSaving(EntitySavingEvent<Course> event) {
        Course course = event.getEntity();
        if (!courseAccess.canEdit(course)) {
            throw new AccessDeniedException("entity", "uic_Course", "update");
        }
        if (entityStates.isNew(course) || course.getId() == null) {
            return;
        }
        Course stored = dataManager.unconstrained().load(Course.class)
                .id(course.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(null);
        if (stored == null) {
            return;
        }
        boolean sharingChanged = !Objects.equals(stored.getSharing(), course.getSharing());
        boolean ownerChanged = entityStates.isLoaded(course, "owner")
                && !courseAccess.sameUser(stored.getOwner(), course.getOwner());
        if ((sharingChanged || ownerChanged) && !courseAccess.canManageAccess(course)) {
            throw new AccessDeniedException("entity", "uic_Course", "update");
        }
    }

    @EventListener
    public void onCoauthorSaving(EntitySavingEvent<CourseCoauthor> event) {
        assertManage(event.getEntity().getCourse());
    }

    @EventListener
    public void onSharedSaving(EntitySavingEvent<CourseSharedWith> event) {
        assertManage(event.getEntity().getCourse());
    }

    private void assertManage(Course course) {
        if (!courseAccess.canManageAccess(course)) {
            throw new AccessDeniedException("entity", "uic_Course", "update");
        }
    }
}
