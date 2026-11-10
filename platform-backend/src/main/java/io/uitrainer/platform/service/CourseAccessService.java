package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.entity.KeyValueEntity;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseSharing;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CourseAccessService {

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final CurrentUserAccess access;

    public CourseAccessService(DataManager dataManager, EntityStates entityStates, CurrentUserAccess access) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.access = access;
    }

    public boolean canEdit(@Nullable Course course) {
        if (access.isAdmin()) {
            return true;
        }
        Course loaded = loaded(course);
        if (loaded == null) {
            return false;
        }
        return isOwner(loaded) || isCoauthor(loaded);
    }

    public boolean canManageAccess(@Nullable Course course) {
        if (access.isAdmin()) {
            return true;
        }
        Course loaded = loaded(course);
        return loaded != null && isOwner(loaded);
    }

    public boolean canLead(@Nullable Course course) {
        if (canEdit(course)) {
            return true;
        }
        Course loaded = loaded(course);
        if (loaded == null) {
            return false;
        }
        CourseSharing sharing = loaded.getSharing();
        if (sharing == CourseSharing.ALL_TEACHERS) {
            return access.isTrainer();
        }
        return sharing == CourseSharing.SELECTED && isSharedWith(loaded);
    }

    public boolean canAssignToGroup(@Nullable Course course, @Nullable StudyGroup group) {
        if (access.isAdmin()) {
            return true;
        }
        if (!canLead(course) || group == null) {
            return false;
        }
        String groupOwner = ownerUsername(group);
        return groupOwner != null && groupOwner.equals(access.username());
    }

    public String roleLabel(@Nullable Course course) {
        if (course == null) {
            return "";
        }
        return roleLabels(List.of(course)).getOrDefault(course.getId(), "");
    }

    public Map<UUID, String> roleLabels(Collection<Course> courses) {
        Map<UUID, String> result = new HashMap<>();
        if (courses == null || courses.isEmpty()) {
            return result;
        }
        List<UUID> ids = courses.stream().map(Course::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return result;
        }
        if (access.isAdmin()) {
            for (UUID id : ids) {
                result.put(id, "Автор");
            }
            return result;
        }
        Set<UUID> owned = ownerIds(ids);
        Set<UUID> coauthorIds = idSet(
                "select a.course.id from uic_CourseCoauthor a where a.trainer.username = :u and a.course.id in :ids",
                ids);
        Set<UUID> sharedIds = idSet(
                "select s.course.id from uic_CourseSharedWith s where s.trainer.username = :u and s.course.id in :ids "
                        + "and s.course.sharing = 'SELECTED'",
                ids);
        for (Course course : courses) {
            UUID id = course.getId();
            if (id == null) {
                continue;
            }
            if (owned.contains(id)) {
                result.put(id, "Автор");
            } else if (coauthorIds.contains(id)) {
                result.put(id, "Соавтор");
            } else if (course.getSharing() == CourseSharing.ALL_TEACHERS && access.isTrainer()) {
                result.put(id, "Ведение");
            } else if (course.getSharing() == CourseSharing.SELECTED && sharedIds.contains(id) && access.isTrainer()) {
                result.put(id, "Ведение");
            } else {
                result.put(id, "");
            }
        }
        return result;
    }

    private Set<UUID> ownerIds(List<UUID> ids) {
        Set<UUID> result = new HashSet<>();
        List<KeyValueEntity> rows = dataManager.unconstrained().loadValues(
                        "select c.id, o.username from uic_Course c join c.owner o where c.id in :ids")
                .properties("courseId", "username")
                .parameter("ids", ids)
                .list();
        for (KeyValueEntity row : rows) {
            if (access.username().equals(row.getValue("username"))) {
                result.add(row.getValue("courseId"));
            }
        }
        return result;
    }

    private Set<UUID> idSet(String query, List<UUID> ids) {
        Set<UUID> result = new HashSet<>();
        if (ids.isEmpty()) {
            return result;
        }
        List<KeyValueEntity> rows = dataManager.unconstrained().loadValues(query)
                .properties("courseId")
                .parameter("u", access.username())
                .parameter("ids", ids)
                .list();
        for (KeyValueEntity row : rows) {
            UUID id = row.getValue("courseId");
            if (id != null) {
                result.add(id);
            }
        }
        return result;
    }

    public String unavailableForGroupMessage(@Nullable Course course) {
        Course loaded = loaded(course);
        String title = loaded == null || loaded.getTitle() == null ? "" : loaded.getTitle();
        return "Курс «" + title + "» недоступен вам для групп";
    }

    @Nullable
    private Course loaded(@Nullable Course course) {
        if (course == null) {
            return null;
        }
        if (course.getId() == null || entityStates.isNew(course)) {
            return course;
        }
        return dataManager.unconstrained().load(Course.class)
                .id(course.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(course);
    }

    private boolean isOwner(Course course) {
        if (course.getId() == null || entityStates.isNew(course)) {
            User owner = course.getOwner();
            return owner != null && access.username().equals(owner.getUsername());
        }
        Long count = dataManager.unconstrained().loadValue(
                        "select count(c) from uic_Course c where c.id = :id and c.owner.username = :u", Long.class)
                .parameter("id", course.getId())
                .parameter("u", access.username())
                .one();
        return count != null && count > 0;
    }

    private boolean isCoauthor(Course course) {
        UUID id = course.getId();
        if (id == null) {
            return false;
        }
        Long count = dataManager.unconstrained().loadValue(
                        "select count(a) from uic_CourseCoauthor a where a.course.id = :c and a.trainer.username = :u",
                        Long.class)
                .parameter("c", id)
                .parameter("u", access.username())
                .one();
        return count != null && count > 0;
    }

    private boolean isSharedWith(Course course) {
        UUID id = course.getId();
        if (id == null) {
            return false;
        }
        Long count = dataManager.unconstrained().loadValue(
                        "select count(s) from uic_CourseSharedWith s where s.course.id = :c and s.trainer.username = :u",
                        Long.class)
                .parameter("c", id)
                .parameter("u", access.username())
                .one();
        return count != null && count > 0;
    }

    @Nullable
    private String ownerUsername(StudyGroup group) {
        if (group.getId() == null) {
            return group.getOwner() == null ? null : group.getOwner().getUsername();
        }
        StudyGroup loaded = dataManager.unconstrained().load(StudyGroup.class)
                .id(group.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME))
                .optional()
                .orElse(null);
        if (loaded == null || loaded.getOwner() == null) {
            return group.getOwner() == null ? null : group.getOwner().getUsername();
        }
        return loaded.getOwner().getUsername();
    }

    public boolean sameUser(@Nullable User left, @Nullable User right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getId() != null && right.getId() != null) {
            return Objects.equals(left.getId(), right.getId());
        }
        return Objects.equals(left.getUsername(), right.getUsername());
    }
}
