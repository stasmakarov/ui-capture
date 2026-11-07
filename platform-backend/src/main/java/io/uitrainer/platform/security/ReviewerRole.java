package io.uitrainer.platform.security;

import io.uitrainer.platform.entity.*;
import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/** Проверяющий: видит все уроки, одобряет или возвращает с комментарием. */
@ResourceRole(name = "Проверяющий", code = ReviewerRole.CODE)
public interface ReviewerRole {
    String CODE = "reviewer";

    @EntityPolicy(entityClass = Lesson.class, actions = {EntityPolicyAction.READ, EntityPolicyAction.UPDATE})
    @EntityAttributePolicy(entityClass = Lesson.class, attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    void lesson();

    @EntityPolicy(entityClass = LessonVersion.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = LessonVersion.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonVersion();

    @EntityPolicy(entityClass = LessonAsset.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = LessonAsset.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void lessonAsset();

    @EntityPolicy(entityClass = StepRef.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = StepRef.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void stepRef();

    @EntityPolicy(entityClass = LifecycleTransition.class, actions = {EntityPolicyAction.CREATE, EntityPolicyAction.READ})
    @EntityAttributePolicy(entityClass = LifecycleTransition.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void transition();

    @EntityPolicy(entityClass = Course.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = Course.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void course();

    @EntityPolicy(entityClass = User.class, actions = EntityPolicyAction.READ)
    @EntityAttributePolicy(entityClass = User.class, attributes = "*", action = EntityAttributePolicyAction.VIEW)
    void user();

    @ViewPolicy(viewIds = {PlatformViews.REVIEW_QUEUE, PlatformViews.LESSON_DETAIL, PlatformViews.LESSON_PREVIEW, PlatformViews.COURSE_LIST, PlatformViews.COURSE_DETAIL})
    @MenuPolicy(menuIds = {PlatformViews.REVIEW_QUEUE, PlatformViews.COURSE_LIST})
    void views();
}
