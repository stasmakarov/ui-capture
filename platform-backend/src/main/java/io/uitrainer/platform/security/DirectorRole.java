package io.uitrainer.platform.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.CourseCoauthor;
import io.uitrainer.platform.entity.CourseSharedWith;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupCourseProgress;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonAttempt;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;

/** Директор: все преподаватели и их курсы, только просмотр. Без ролей, токенов и записи в студии. */
@ResourceRole(name = "Директор", code = DirectorRole.CODE)
public interface DirectorRole {
    String CODE = "director";

    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = User.class, attributes = {"id", "version", "username", "firstName", "lastName", "email", "active", "timeZoneId"}, action = EntityAttributePolicyAction.VIEW)
    void user();

    @EntityPolicy(entityClass = RoleAssignmentEntity.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = RoleAssignmentEntity.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void roleAssignment();

    @EntityPolicy(entityClass = Course.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Course.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void course();

    @EntityPolicy(entityClass = CourseCoauthor.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = CourseCoauthor.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void courseCoauthor();

    @EntityPolicy(entityClass = CourseSharedWith.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = CourseSharedWith.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void courseSharedWith();

    @EntityPolicy(entityClass = Lesson.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Lesson.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lesson();

    @EntityPolicy(entityClass = LessonVersion.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = LessonVersion.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonVersion();

    @EntityPolicy(entityClass = StudyGroup.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = StudyGroup.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void studyGroup();

    @EntityPolicy(entityClass = GroupCourse.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = GroupCourse.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void groupCourse();

    @EntityPolicy(entityClass = GroupMember.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = GroupMember.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void groupMember();

    @EntityPolicy(entityClass = GroupCourseProgress.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = GroupCourseProgress.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void groupCourseProgress();

    @EntityPolicy(entityClass = LessonAttempt.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = LessonAttempt.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonAttempt();

    @ViewPolicy(viewIds = {PlatformViews.DIRECTOR_HOME, PlatformViews.TEACHER_COURSES, PlatformViews.COURSE_DETAIL, PlatformViews.COURSE_COAUTHOR_DETAIL, PlatformViews.COURSE_SHARED_WITH_DETAIL, PlatformViews.GROUP_LIST, PlatformViews.GROUP_DETAIL, PlatformViews.GROUP_COURSE_DETAIL, PlatformViews.GROUP_MEMBER_DETAIL})
    @MenuPolicy(menuIds = {PlatformViews.DIRECTOR_HOME, PlatformViews.GROUP_LIST})
    void views();
}
