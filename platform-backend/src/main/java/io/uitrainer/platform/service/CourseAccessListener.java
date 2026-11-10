package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.core.security.AccessDeniedException;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseCoauthor;
import io.uitrainer.platform.entity.CourseSharedWith;
import io.uitrainer.platform.entity.User;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;

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
        CourseCoauthor row = event.getEntity();
        assertManage(row.getCourse());
        if (!entityStates.isNew(row) && row.getId() != null) {
            CourseCoauthor stored = dataManager.unconstrained().load(CourseCoauthor.class)
                    .id(row.getId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE))
                    .optional()
                    .orElse(null);
            if (stored != null) {
                assertManage(stored.getCourse());
            }
        }
        assertUniqueCoauthor(row);
    }

    @EventListener
    public void onSharedSaving(EntitySavingEvent<CourseSharedWith> event) {
        CourseSharedWith row = event.getEntity();
        assertManage(row.getCourse());
        if (!entityStates.isNew(row) && row.getId() != null) {
            CourseSharedWith stored = dataManager.unconstrained().load(CourseSharedWith.class)
                    .id(row.getId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.BASE))
                    .optional()
                    .orElse(null);
            if (stored != null) {
                assertManage(stored.getCourse());
            }
        }
        assertUniqueShared(row);
    }

    private void assertManage(Course course) {
        if (!courseAccess.canManageAccess(course)) {
            throw new AccessDeniedException("entity", "uic_Course", "update");
        }
    }

    private void assertUniqueCoauthor(CourseCoauthor row) {
        UUID courseId = idOf(row.getCourse());
        UUID trainerId = idOf(row.getTrainer());
        if (courseId == null || trainerId == null) {
            return;
        }
        var q = dataManager.unconstrained().loadValue(
                "select count(a) from uic_CourseCoauthor a where a.course.id = :c and a.trainer.id = :t"
                        + (row.getId() == null ? "" : " and a.id <> :id"),
                Long.class)
                .parameter("c", courseId)
                .parameter("t", trainerId);
        if (row.getId() != null) {
            q = q.parameter("id", row.getId());
        }
        Long count = q.one();
        if (count != null && count > 0) {
            throw new ConflictException("Преподаватель уже в списке соавторов");
        }
    }

    private void assertUniqueShared(CourseSharedWith row) {
        UUID courseId = idOf(row.getCourse());
        UUID trainerId = idOf(row.getTrainer());
        if (courseId == null || trainerId == null) {
            return;
        }
        var q = dataManager.unconstrained().loadValue(
                "select count(s) from uic_CourseSharedWith s where s.course.id = :c and s.trainer.id = :t"
                        + (row.getId() == null ? "" : " and s.id <> :id"),
                Long.class)
                .parameter("c", courseId)
                .parameter("t", trainerId);
        if (row.getId() != null) {
            q = q.parameter("id", row.getId());
        }
        Long count = q.one();
        if (count != null && count > 0) {
            throw new ConflictException("Преподаватель уже в списке выбранных");
        }
    }

    private UUID idOf(Course course) {
        return course == null ? null : course.getId();
    }

    private UUID idOf(User trainer) {
        return trainer == null ? null : trainer.getId();
    }
}
