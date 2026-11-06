package io.uitrainer.platform.service;

import io.jmix.core.event.EntitySavingEvent;
import io.uitrainer.platform.entity.LessonAsset;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.StepRef;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** LessonVersion и её файлы/шаги неизменяемы: только вставка. */
@Component
public class LessonVersionImmutability {

    @EventListener
    void onVersionSaving(EntitySavingEvent<LessonVersion> event) {
        if (!event.isNewEntity()) throw new IllegalStateException("Версия урока неизменяема: загрузите новую версию из студии");
    }

    @EventListener
    void onAssetSaving(EntitySavingEvent<LessonAsset> event) {
        if (!event.isNewEntity()) throw new IllegalStateException("Файлы версии урока неизменяемы");
    }

    @EventListener
    void onStepSaving(EntitySavingEvent<StepRef> event) {
        if (!event.isNewEntity()) throw new IllegalStateException("Шаги версии урока неизменяемы");
    }
}
