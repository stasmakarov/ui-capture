package io.uitrainer.platform.view;

import io.jmix.core.security.AccessDeniedException;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.exception.AbstractUiExceptionHandler;
import io.uitrainer.platform.service.AccessException;
import io.uitrainer.platform.service.ConflictException;
import org.springframework.stereotype.Component;

@Component("uic_UiBusinessExceptionHandler")
public class UiBusinessExceptionHandler extends AbstractUiExceptionHandler {

    private final Notifications notifications;

    public UiBusinessExceptionHandler(Notifications notifications) {
        super(ConflictException.class.getName(), AccessDeniedException.class.getName(), AccessException.class.getName());
        this.notifications = notifications;
    }

    @Override
    protected void doHandle(String className, String message, Throwable throwable) {
        String text;
        if (throwable instanceof AccessDeniedException) {
            text = "Недостаточно прав";
        } else {
            text = throwable.getMessage();
            if (text == null || text.isBlank()) {
                text = "Недостаточно прав";
            }
        }
        notifications.create(text).withType(Notifications.Type.ERROR).show();
    }
}
