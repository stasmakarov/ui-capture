package io.uitrainer.platform.security;

import io.uitrainer.platform.entity.*;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/** Ученик: пока нет портала — только опубликованные уроки, без черновиков и проверки. */
@ResourceRole(name = "Ученик", code = StudentRole.CODE)
public interface StudentRole {
    String CODE = "student";

    @EntityPolicy(entityClass = Lesson.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Lesson.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lesson();

    @EntityPolicy(entityClass = LessonVersion.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = LessonVersion.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonVersion();

    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = User.class, attributes = {"id", "username", "firstName", "lastName"}, action = EntityAttributePolicyAction.VIEW)
    void user();

    @EntityPolicy(entityClass = Course.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Course.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void course();

    @EntityPolicy(entityClass = StudyGroup.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = StudyGroup.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void studyGroup();

    @EntityPolicy(entityClass = GroupCourse.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = GroupCourse.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void groupCourse();

    @EntityPolicy(entityClass = GroupMember.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = GroupMember.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void groupMember();

    @EntityPolicy(entityClass = GroupCourseProgress.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = GroupCourseProgress.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void groupCourseProgress();

    @EntityPolicy(entityClass = LessonAttempt.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.CREATE, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = LessonAttempt.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void lessonAttempt();

    @ViewPolicy(viewIds = {PlatformViews.STUDENT_HOME, PlatformViews.STUDENT_GROUP, PlatformViews.STUDENT_COURSE, PlatformViews.LESSON_PREVIEW})
    @MenuPolicy(menuIds = PlatformViews.STUDENT_HOME)
    void views();
}
