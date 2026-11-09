package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.core.FetchPlan;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.StudentRole;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class GroupAssignmentListener {

    private final DataManager dataManager;
    private final EntityStates entityStates;
    private final CurrentUserAccess access;
    private final GroupProgressService groupProgress;

    public GroupAssignmentListener(DataManager dataManager, EntityStates entityStates,
                                   CurrentUserAccess access, GroupProgressService groupProgress) {
        this.dataManager = dataManager;
        this.entityStates = entityStates;
        this.access = access;
        this.groupProgress = groupProgress;
    }

    @EventListener
    public void onGroupCourseSaving(EntitySavingEvent<GroupCourse> event) {
        GroupCourse line = event.getEntity();
        if (line.getCourse() == null || line.getGroup() == null) {
            return;
        }
        StudyGroup group = line.getGroup();
        UUID courseOwner = ownerId(line.getCourse());
        UUID groupOwner = ownerId(group);
        if (courseOwner == null || groupOwner == null || !courseOwner.equals(groupOwner)) {
            throw new ConflictException("В группу можно добавить только свои курсы");
        }
        assertOwner(group);
        if (entityStates.isNew(line) && (line.getSortOrder() == null || line.getSortOrder() == 0)) {
            line.setSortOrder(nextSortOrder(group, line));
        }
    }

    @EventListener
    public void onGroupMemberSaving(EntitySavingEvent<GroupMember> event) {
        GroupMember member = event.getEntity();
        if (member.getStudent() == null) {
            return;
        }
        if (member.getGroup() != null) {
            assertOwner(member.getGroup());
        }
        User student = member.getStudent();
        if (student.getUsername() == null && student.getId() != null) {
            student = dataManager.unconstrained().load(User.class).id(student.getId()).one();
        }
        boolean studentRole = dataManager.unconstrained().load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :c and r.roleType = 'resource'")
                .parameter("u", student.getUsername())
                .parameter("c", StudentRole.CODE)
                .optional()
                .isPresent();
        if (!studentRole) {
            throw new ConflictException("В группу можно добавить только ученика");
        }
    }

    @EventListener
    public void onGroupCourseChanged(EntityChangedEvent<GroupCourse> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            UUID groupId = oldId(event, "group");
            UUID courseId = oldId(event, "course");
            if (groupId != null && courseId != null) {
                groupProgress.deleteProgressForCourse(groupId, courseId);
            }
            return;
        }
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            GroupCourse line = dataManager.unconstrained().load(event.getEntityId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("group", FetchPlan.BASE)
                            .add("course", FetchPlan.BASE))
                    .one();
            for (GroupMember member : members(line.getGroup().getId())) {
                groupProgress.recalculate(line.getGroup(), member.getStudent(), line.getCourse());
            }
        }
    }

    @EventListener
    public void onGroupMemberChanged(EntityChangedEvent<GroupMember> event) {
        if (event.getType() == EntityChangedEvent.Type.DELETED) {
            UUID groupId = oldId(event, "group");
            UUID studentId = oldId(event, "student");
            if (groupId != null && studentId != null) {
                groupProgress.deleteProgressForMember(groupId, studentId);
            }
            return;
        }
        if (event.getType() == EntityChangedEvent.Type.CREATED) {
            GroupMember member = dataManager.unconstrained().load(event.getEntityId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                            .add("group", FetchPlan.BASE)
                            .add("student", FetchPlan.BASE))
                    .one();
            groupProgress.recalculateStudentInGroup(member.getGroup(), member.getStudent());
        }
    }

    private void assertOwner(StudyGroup group) {
        if (access.isAdmin() || "system".equals(access.username())) {
            return;
        }
        UUID ownerId = ownerId(group);
        User current = dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .optional()
                .orElse(null);
        if (ownerId == null || current == null || !ownerId.equals(current.getId())) {
            throw new ConflictException("Состав группы может менять только владелец");
        }
    }

    private int nextSortOrder(StudyGroup group, GroupCourse current) {
        if (entityStates.isNew(group) || group.getId() == null) {
            int max = 0;
            if (group.getCourses() != null) {
                for (GroupCourse gc : group.getCourses()) {
                    if (gc == current || gc.getSortOrder() == null) continue;
                    max = Math.max(max, gc.getSortOrder());
                }
            }
            return max + 1;
        }
        return groupProgress.nextSortOrder(group);
    }

    private java.util.List<GroupMember> members(UUID groupId) {
        return dataManager.unconstrained().load(GroupMember.class)
                .query("select m from uic_GroupMember m where m.group.id = :g")
                .parameter("g", groupId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE)
                        .add("student", FetchPlan.BASE)
                        .add("group", FetchPlan.BASE))
                .list();
    }

    private UUID oldId(EntityChangedEvent<?> event, String attribute) {
        var ref = event.getChanges().getOldReferenceId(attribute);
        return ref == null ? null : (UUID) ref.getValue();
    }

    private UUID ownerId(Course course) {
        if (course.getId() == null) {
            return null;
        }
        Course loaded = dataManager.unconstrained().load(Course.class)
                .id(course.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.BASE))
                .one();
        return loaded.getOwner() == null ? null : loaded.getOwner().getId();
    }

    private UUID ownerId(StudyGroup group) {
        if (entityStates.isNew(group) || group.getId() == null) {
            if (group.getOwner() != null) {
                return group.getOwner().getId();
            }
            return null;
        }
        StudyGroup loaded = dataManager.unconstrained().load(StudyGroup.class)
                .id(group.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.BASE))
                .optional()
                .orElse(null);
        if (loaded == null) {
            return group.getOwner() == null ? null : group.getOwner().getId();
        }
        return loaded.getOwner() == null ? null : loaded.getOwner().getId();
    }
}
