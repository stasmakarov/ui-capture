package io.uicapture.platform.security;

import io.uicapture.platform.entity.*;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/** Тренер: записывает уроки в студии, ведёт их ЖЦ, выпускает свои токены студии. */
@ResourceRole(name = "Тренер", code = TrainerRole.CODE)
public interface TrainerRole {
    String CODE = "trainer";

    @EntityPolicy(entityClass = Lesson.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Lesson.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void lesson();

    @EntityPolicy(entityClass = LessonVersion.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = LessonVersion.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonVersion();

    @EntityPolicy(entityClass = LessonAsset.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = LessonAsset.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonAsset();

    @EntityPolicy(entityClass = StepRef.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = StepRef.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void stepRef();

    @EntityPolicy(entityClass = LifecycleTransition.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = LifecycleTransition.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void transition();

    @EntityPolicy(entityClass = StudioToken.class, actions = EntityPolicyAction.ALL)
    @EntityAttributePolicy(entityClass = StudioToken.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void studioToken();

    @EntityPolicy(entityClass = StudioTicket.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = StudioTicket.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void studioTicket();

    @EntityPolicy(entityClass = Course.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Course.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void course();

    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void user();

    @ViewPolicy(viewIds = {PlatformViews.LESSON_LIST, PlatformViews.LESSON_DETAIL, PlatformViews.LESSON_PREVIEW, PlatformViews.STUDIO_TOKENS})
    @MenuPolicy(menuIds = {PlatformViews.LESSON_LIST, PlatformViews.STUDIO_TOKENS})
    void views();
}
