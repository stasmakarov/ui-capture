package io.uitrainer.platform.security;

import io.uitrainer.platform.entity.*;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/** Преподаватель: свои курсы, запись в студии и ЖЦ уроков. Экран токенов — у администратора. */
@ResourceRole(name = "Тренер", code = TrainerRole.CODE)
public interface TrainerRole {
    String CODE = "trainer";

    @EntityPolicy(entityClass = Lesson.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ, EntityPolicyAction.UPDATE, EntityPolicyAction.DELETE})
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

    @EntityPolicy(entityClass = Course.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ, EntityPolicyAction.UPDATE, EntityPolicyAction.DELETE})
    @EntityAttributePolicy(entityClass = Course.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void course();

    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void user();

    @ViewPolicy(viewIds = {PlatformViews.TRAINER_HOME, PlatformViews.LESSON_QUEUE, PlatformViews.LESSON_DETAIL, PlatformViews.LESSON_EDIT, PlatformViews.LESSON_PREVIEW, PlatformViews.COURSE_LIST, PlatformViews.COURSE_DETAIL})
    @MenuPolicy(menuIds = {PlatformViews.TRAINER_HOME, PlatformViews.COURSE_LIST})
    void views();
}
