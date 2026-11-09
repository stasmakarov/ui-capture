package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.StudyGroup;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class GroupLifecycleService {

    private final DataManager dataManager;
    private final CurrentUserAccess access;

    public GroupLifecycleService(DataManager dataManager, CurrentUserAccess access) {
        this.dataManager = dataManager;
        this.access = access;
    }

    @Transactional
    public StudyGroup activate(UUID groupId) {
        StudyGroup group = load(groupId);
        if (group.getStatus() != GroupStatus.CREATED) {
            throw new ConflictException("Активировать можно только группу в статусе «создана»");
        }
        group.setStatus(GroupStatus.ACTIVE);
        return dataManager.save(group);
    }

    @Transactional
    public StudyGroup close(UUID groupId) {
        StudyGroup group = load(groupId);
        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw new ConflictException("Закрыть можно только активную группу");
        }
        group.setStatus(GroupStatus.CLOSED);
        return dataManager.save(group);
    }

    private StudyGroup load(UUID groupId) {
        StudyGroup group = dataManager.load(StudyGroup.class)
                .id(groupId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME))
                .one();
        if (!access.isAdmin() && !access.username().equals(group.getOwner().getUsername())) {
            throw new ConflictException("Группу может менять только владелец");
        }
        return group;
    }
}
