package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.uitrainer.platform.entity.LessonAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LessonAttemptInsert {

    private final DataManager dataManager;

    public LessonAttemptInsert(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public LessonAttempt insert(LessonAttempt attempt) {
        return dataManager.save(attempt);
    }
}
