package io.uicapture.platform.service;

import io.uicapture.platform.entity.Lesson;
import io.uicapture.platform.entity.LessonVersion;

public record ImportResult(Lesson lesson, LessonVersion version, boolean newLesson) {
}
