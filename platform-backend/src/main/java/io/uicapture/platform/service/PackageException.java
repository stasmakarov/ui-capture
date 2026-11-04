package io.uicapture.platform.service;

/** Ошибка содержимого пакета урока: отдаётся клиенту как 400. */
public class PackageException extends RuntimeException {
    public PackageException(String message) {
        super(message);
    }
}
