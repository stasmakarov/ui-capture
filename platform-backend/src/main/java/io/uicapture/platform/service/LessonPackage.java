package io.uicapture.platform.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/** Проверенный пакет .lesson.zip: манифест, lesson.json и файлы по путям. */
public record LessonPackage(JsonNode manifest, String manifestJson, JsonNode lesson, String lessonJson,
                            Map<String, byte[]> files, String packageSha256) {

    public String lessonId() {
        return lesson.path("id").asText("");
    }

    public String title() {
        return lesson.path("title").asText("");
    }
}
