package io.uitrainer.platform.service;

/** Действие запрещено текущему пользователю: 403 в REST, уведомление в UI. */
public class AccessException extends RuntimeException {
    public AccessException(String message) {
        super(message);
    }
}
