package io.uitrainer.platform.view;

import io.jmix.core.security.AccessDeniedException;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.exception.AbstractUiExceptionHandler;
import io.uitrainer.platform.service.AccessException;
import io.uitrainer.platform.service.ConflictException;
import jakarta.persistence.PersistenceException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;

@Component("uic_UiBusinessExceptionHandler")
public class UiBusinessExceptionHandler extends AbstractUiExceptionHandler {

    private final Notifications notifications;

    public UiBusinessExceptionHandler(Notifications notifications) {
        super(ConflictException.class.getName(), AccessDeniedException.class.getName(), AccessException.class.getName(),
                DataIntegrityViolationException.class.getName(), PersistenceException.class.getName());
        this.notifications = notifications;
    }

    @Override
    protected void doHandle(String className, String message, Throwable throwable) {
        String text;
        if (courseListDuplicate(throwable, "UQ_UIC_COURSE_COAUTHOR")) {
            text = "Преподаватель уже в списке соавторов";
        } else if (courseListDuplicate(throwable, "UQ_UIC_COURSE_SHARED_WITH")) {
            text = "Преподаватель уже в списке выбранных";
        } else if (throwable instanceof AccessDeniedException) {
            text = "Недостаточно прав";
        } else if (userReferenced(throwable)) {
            text = "Нельзя удалить пользователя, пока на него ссылаются курсы, уроки или группы";
        } else {
            text = throwable.getMessage();
            if (text == null || text.isBlank()) {
                text = "Недостаточно прав";
            }
        }
        notifications.create(text).withType(Notifications.Type.ERROR).show();
    }

    private static boolean userReferenced(Throwable ex) {
        while (ex != null) {
            String text = ex.getMessage();
            if (text != null) {
                String upper = text.toUpperCase();
                if (upper.contains("UIC_USER")
                        || (upper.contains("FK_UIC_") && upper.contains("USER"))
                        || upper.contains("OWNER_ID")
                        || upper.contains("AUTHOR_ID")
                        || (upper.contains("STUDENT_ID") && upper.contains("23503"))) {
                    return true;
                }
            }
            if (ex instanceof SQLException sql && "23503".equals(sql.getSQLState())) {
                String msg = sql.getMessage();
                if (msg != null && msg.toUpperCase().contains("USER")) {
                    return true;
                }
            }
            ex = ex.getCause();
        }
        return false;
    }

    private static boolean courseListDuplicate(Throwable ex, String constraint) {
        while (ex != null) {
            if (ex instanceof SQLException sql && "23505".equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().toUpperCase().contains(constraint)) {
                return true;
            }
            String text = ex.getMessage();
            if (text != null && text.toUpperCase().contains(constraint) && text.contains("23505")) {
                return true;
            }
            if (text != null && text.toUpperCase().contains(constraint)
                    && (ex instanceof DataIntegrityViolationException || ex instanceof PersistenceException)) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }
}
