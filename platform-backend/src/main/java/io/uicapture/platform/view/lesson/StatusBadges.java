package io.uicapture.platform.view.lesson;

import com.vaadin.flow.component.html.Span;
import io.uicapture.platform.entity.LessonStatus;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Подписи статусов и даты для экранов. */
public final class StatusBadges {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private StatusBadges() {
    }

    public static String label(LessonStatus status) {
        if (status == null) return "—";
        return switch (status) {
            case DRAFT -> "Черновик";
            case REVIEW -> "На проверке";
            case PUBLISHED -> "Опубликован";
            case ARCHIVED -> "В архиве";
        };
    }

    public static String action(LessonStatus from, LessonStatus to) {
        return switch (to) {
            case REVIEW -> "Отправить на проверку";
            case PUBLISHED -> "Опубликовать";
            case DRAFT -> from == LessonStatus.REVIEW ? "Вернуть на доработку" : "В черновик";
            case ARCHIVED -> "В архив";
        };
    }

    public static Span badge(LessonStatus status) {
        Span span = new Span(label(status));
        String theme = switch (status == null ? LessonStatus.DRAFT : status) {
            case DRAFT -> "badge";
            case REVIEW -> "badge contrast";
            case PUBLISHED -> "badge success";
            case ARCHIVED -> "badge error";
        };
        span.getElement().setAttribute("theme", theme);
        span.addClassName("uic-status-" + (status == null ? "none" : status.getId().toLowerCase()));
        return span;
    }

    public static String date(OffsetDateTime value) {
        return value == null ? "" : value.atZoneSameInstant(ZoneId.systemDefault()).format(FORMAT);
    }
}
