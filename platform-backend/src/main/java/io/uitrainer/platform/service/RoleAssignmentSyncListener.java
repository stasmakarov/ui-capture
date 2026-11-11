package io.uitrainer.platform.service;

import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Экран назначений ролей пишет только RoleAssignmentEntity — row-level trainer/student подтягиваем здесь. */
@Component
public class RoleAssignmentSyncListener {

    private final UserProvisioning provisioning;

    public RoleAssignmentSyncListener(UserProvisioning provisioning) {
        this.provisioning = provisioning;
    }

    @EventListener
    public void onSaving(EntitySavingEvent<RoleAssignmentEntity> event) {
        RoleAssignmentEntity row = event.getEntity();
        if (row.getUsername() == null) return;
        if (UserProvisioning.ROW_LEVEL.equals(row.getRoleType())) return;
        provisioning.syncRowLevels(row.getUsername(), row.getRoleCode());
    }

    @EventListener
    public void onChanged(EntityChangedEvent<RoleAssignmentEntity> event) {
        if (event.getType() != EntityChangedEvent.Type.DELETED) return;
        String username = event.getChanges().getOldValue("username");
        if (username != null) provisioning.syncRowLevels(username);
    }
}
