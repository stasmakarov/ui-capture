package io.uitrainer.platform.service;

import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonVersion;

public record ImportResult(Lesson lesson, LessonVersion version, boolean newLesson) {
}
