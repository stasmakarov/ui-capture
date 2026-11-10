package io.uitrainer.platform.service;

import io.jmix.core.EntityStates;
import io.jmix.core.event.EntityChangedEvent;
import io.jmix.core.event.EntitySavingEvent;
import io.uitrainer.platform.entity.Lesson;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LessonOrderListener {

    private final EntityStates entityStates;
    private final LessonOrderService lessonOrder;

    public LessonOrderListener(EntityStates entityStates, LessonOrderService lessonOrder) {
        this.entityStates = entityStates;
        this.lessonOrder = lessonOrder;
    }

    @EventListener
    public void onLessonSaving(EntitySavingEvent<Lesson> event) {
        Lesson lesson = event.getEntity();
        if (entityStates.isNew(lesson) && (lesson.getSortOrder() == null || lesson.getSortOrder() == 0)) {
            lesson.setSortOrder(lessonOrder.nextSortOrder(lesson.getCourse()));
        }
    }

    @EventListener
    public void onLessonChanged(EntityChangedEvent<Lesson> event) {
        if (event.getType() != EntityChangedEvent.Type.DELETED) {
            return;
        }
        UUID courseId = oldId(event, "course");
        lessonOrder.normalizeCourse(courseId);
    }

    private UUID oldId(EntityChangedEvent<?> event, String attribute) {
        var ref = event.getChanges().getOldReferenceId(attribute);
        return ref == null ? null : (UUID) ref.getValue();
    }
}
