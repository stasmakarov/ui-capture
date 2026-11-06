package io.uicapture.platform.view;

import io.jmix.core.DeletePolicyException;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.exception.AbstractUiExceptionHandler;
import org.springframework.stereotype.Component;

/** Русский текст вместо «Unable to delete … because there are references». */
@Component("uic_DeletePolicyExceptionHandler")
public class DeletePolicyExceptionHandler extends AbstractUiExceptionHandler {

    private final Notifications notifications;

    public DeletePolicyExceptionHandler(Notifications notifications) {
        super(DeletePolicyException.class.getName());
        this.notifications = notifications;
    }

    @Override
    protected void doHandle(String className, String message, Throwable throwable) {
        String text = "Нельзя удалить запись, пока на неё ссылаются другие данные";
        if (throwable instanceof DeletePolicyException denied) {
            text = switch (denied.getRefEntity()) {
                case "uic_Lesson" -> "Нельзя удалить курс, пока к нему привязаны уроки";
                case "uic_LessonVersion", "uic_LifecycleTransition" ->
                        "Нельзя удалить урок, у которого уже есть версии. Он остаётся в списке «Уроки».";
                default -> text;
            };
        }
        notifications.create(text).withType(Notifications.Type.ERROR).show();
    }
}
