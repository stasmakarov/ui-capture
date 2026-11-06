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
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonVersion;
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

    @EntityPolicy(entityClass = Lesson.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Lesson.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lesson();

    @EntityPolicy(entityClass = LessonVersion.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = LessonVersion.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonVersion();

    @ViewPolicy(viewIds = {PlatformViews.DIRECTOR_HOME, PlatformViews.TEACHER_COURSES, PlatformViews.COURSE_DETAIL})
    @MenuPolicy(menuIds = PlatformViews.DIRECTOR_HOME)
    void views();
}
