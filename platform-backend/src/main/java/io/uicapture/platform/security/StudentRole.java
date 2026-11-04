package io.uicapture.platform.security;

import io.uicapture.platform.entity.*;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/** Ученик: в M2 только просмотр опубликованных уроков (портал — M3). */
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

    @ViewPolicy(viewIds = {PlatformViews.LESSON_LIST, PlatformViews.LESSON_PREVIEW})
    @MenuPolicy(menuIds = PlatformViews.LESSON_LIST)
    void views();
}
