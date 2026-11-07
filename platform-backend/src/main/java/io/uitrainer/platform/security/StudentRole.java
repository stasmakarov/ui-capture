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

    @ViewPolicy(viewIds = {PlatformViews.STUDENT_HOME, PlatformViews.LESSON_PREVIEW})
    @MenuPolicy(menuIds = PlatformViews.STUDENT_HOME)
    void views();
}
