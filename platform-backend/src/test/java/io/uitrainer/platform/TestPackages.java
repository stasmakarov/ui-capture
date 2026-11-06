package io.uitrainer.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.uitrainer.platform.service.LessonPackageReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Сборка .lesson.zip в тестах — тот же формат, что `npx uitrainer package`. */
public final class TestPackages {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestPackages() {
    }

    public static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static Map<String, byte[]> lessonFiles(String id, String title) {
        ObjectNode lesson = MAPPER.createObjectNode();
        lesson.put("schemaVersion", 2);
        lesson.put("id", id);
        lesson.put("title", title);
        lesson.put("description", "Тестовый урок");
        lesson.put("sourceSite", "https://example.org");
        ArrayNode screens = lesson.putArray("screens");
        ObjectNode screen = screens.addObject();
        screen.put("id", "screen-1");
        screen.put("html", "screens/screen-1.html");
        screen.put("screenshot", "original/screen-1.png");
        ArrayNode steps = lesson.putArray("steps");
        for (int i = 1; i <= 3; i++) {
            ObjectNode step = steps.addObject();
            step.put("id", "step-" + i);
            step.put("screenId", "screen-1");
            step.put("action", i == 2 ? "input" : "click");
            step.put("instruction", "Шаг " + i + ": нажмите кнопку");
        }
        Map<String, byte[]> files = new LinkedHashMap<>();
        try {
            files.put("lesson.json", MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(lesson));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        files.put("screens/screen-1.html", "<html><body><button>Найти</button></body></html>".getBytes(StandardCharsets.UTF_8));
        files.put("original/screen-1.png", new byte[]{(byte) 0x89, 'P', 'N', 'G', 1, 2, 3});
        files.put("trainer.html", ("<!doctype html><title>" + title + "</title><h1>" + title + "</h1>").getBytes(StandardCharsets.UTF_8));
        return files;
    }

    public static byte[] zip(String id, String title, Map<String, byte[]> files) {
        return zip(id, title, files, null, null);
    }

    /** tamperPath — файл, чей хэш в манифесте будет неверным; extraPath — файл, которого нет в манифесте. */
    public static byte[] zip(String id, String title, Map<String, byte[]> files, String tamperPath, String extraPath) {
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("format", "ui-capture-lesson");
        manifest.put("formatVersion", 1);
        manifest.put("schemaVersion", 2);
        manifest.put("id", id);
        manifest.put("title", title);
        manifest.put("createdAt", "2026-11-04T00:00:00.000Z");
        ArrayNode list = manifest.putArray("files");
        files.keySet().stream().sorted().forEach(path -> {
            byte[] bytes = files.get(path);
            ObjectNode item = list.addObject();
            item.put("path", path);
            item.put("sha256", path.equals(tamperPath) ? "0".repeat(64) : LessonPackageReader.sha256(bytes));
            item.put("bytes", bytes.length);
        });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(MAPPER.writeValueAsBytes(manifest));
            zip.closeEntry();
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
            if (extraPath != null) {
                zip.putNextEntry(new ZipEntry(extraPath));
                zip.write(new byte[]{1});
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return out.toByteArray();
    }

    public static byte[] validZip(String id, String title) {
        return zip(id, title, lessonFiles(id, title));
    }
}
