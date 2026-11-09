package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
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

    public GroupAssignmentListener(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @EventListener
    public void onGroupCourseSaving(EntitySavingEvent<GroupCourse> event) {
        GroupCourse line = event.getEntity();
        if (line.getCourse() == null || line.getGroup() == null) {
            return;
        }
        UUID courseOwner = ownerId(line.getCourse());
        UUID groupOwner = ownerId(line.getGroup());
        if (courseOwner == null || groupOwner == null || !courseOwner.equals(groupOwner)) {
            throw new ConflictException("В группу можно добавить только свои курсы");
        }
    }

    @EventListener
    public void onGroupMemberSaving(EntitySavingEvent<GroupMember> event) {
        GroupMember member = event.getEntity();
        if (member.getStudent() == null) {
            return;
        }
        User student = member.getStudent();
        if (student.getUsername() == null) {
            student = dataManager.load(User.class).id(student.getId()).one();
        }
        boolean studentRole = dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :c and r.roleType = 'resource'")
                .parameter("u", student.getUsername())
                .parameter("c", StudentRole.CODE)
                .optional()
                .isPresent();
        if (!studentRole) {
            throw new ConflictException("В группу можно добавить только ученика");
        }
    }

    private UUID ownerId(Course course) {
        if (course.getId() == null) {
            return null;
        }
        Course loaded = dataManager.load(Course.class)
                .id(course.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.BASE))
                .one();
        return loaded.getOwner() == null ? null : loaded.getOwner().getId();
    }

    private UUID ownerId(StudyGroup group) {
        if (group.getId() == null) {
            return null;
        }
        StudyGroup loaded = dataManager.load(StudyGroup.class)
                .id(group.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.BASE))
                .one();
        return loaded.getOwner() == null ? null : loaded.getOwner().getId();
    }
}
