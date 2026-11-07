package io.uitrainer.platform.security;

import io.jmix.security.model.EntityAttributePolicyAction;
import io.jmix.security.model.EntityPolicyAction;
import io.jmix.security.role.annotation.EntityAttributePolicy;
import io.jmix.security.role.annotation.EntityPolicy;
import io.jmix.security.role.annotation.ResourceRole;
import io.jmix.security.role.annotation.SpecificPolicy;
import io.jmix.securityflowui.role.annotation.MenuPolicy;
import io.jmix.securityflowui.role.annotation.ViewPolicy;

/** Администратор платформы: полный доступ. */
@ResourceRole(name = "Администратор", code = AdminRole.CODE)
public interface AdminRole {
    String CODE = "admin";

    @EntityPolicy(entityName = "*", actions = {EntityPolicyAction.ALL})
    @EntityAttributePolicy(entityName = "*", attributes = "*", action = EntityAttributePolicyAction.MODIFY)
    @ViewPolicy(viewIds = "*")
    @MenuPolicy(menuIds = {
            PlatformViews.ADMIN_HOME,
            PlatformViews.REVIEW_QUEUE,
            PlatformViews.COURSE_LIST,
            PlatformViews.LESSON_LIST,
            PlatformViews.STUDIO_TOKENS,
            PlatformViews.USER_LIST,
            "administration",
            "security",
            "sec_ResourceRoleModel.list",
            "sec_RowLevelRoleModel.list",
            "datatools",
            "datatl_entityInspectorListView"
    })
    @SpecificPolicy(resources = "*")
    void fullAccess();
}
