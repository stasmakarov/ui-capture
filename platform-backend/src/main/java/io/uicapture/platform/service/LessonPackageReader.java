package io.uicapture.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Разбор и проверка .lesson.zip — тот же контракт, что tools/package/lessonPackage.ts (openPackage):
 * manifest.json с SHA-256 и размером каждого файла, lesson.json, каталоги screens/, original/, assets/, audio/.
 */
@Component
public class LessonPackageReader {

    public static final String FORMAT = "ui-capture-lesson";
    public static final int FORMAT_VERSION = 1;
    public static final long MAX_TOTAL_BYTES = 300L * 1024 * 1024;
    private static final Set<String> ROOT_FILES = Set.of("lesson.json", "trainer.html");
    private static final Set<String> DIRS = Set.of("screens", "original", "assets", "audio");
    private static final Pattern LESSON_ID = Pattern.compile("^[a-z0-9-]{2,48}$");

    private final ObjectMapper mapper = new ObjectMapper();

    public LessonPackage read(byte[] archive) {
        if (archive == null || archive.length == 0) {
            throw new PackageException("Пустой запрос: ожидается файл .lesson.zip");
        }
        Map<String, byte[]> files = unzip(archive);
        byte[] manifestBytes = files.remove("manifest.json");
        if (manifestBytes == null) throw new PackageException("В пакете нет manifest.json");
        JsonNode manifest = parseJson(manifestBytes, "manifest.json");
        if (!FORMAT.equals(manifest.path("format").asText()) || manifest.path("formatVersion").asInt() != FORMAT_VERSION) {
            throw new PackageException("Неизвестный формат пакета урока: ожидается " + FORMAT + " v" + FORMAT_VERSION);
        }
        JsonNode listed = manifest.path("files");
        if (!listed.isArray()) throw new PackageException("В manifest.json нет списка files");

        Set<String> listedPaths = new HashSet<>();
        for (JsonNode item : listed) {
            String path = item.path("path").asText("");
            assertPath(path);
            listedPaths.add(path);
            byte[] bytes = files.get(path);
            if (bytes == null) throw new PackageException("В манифесте указан " + path + ", но в архиве его нет");
            String expected = item.path("sha256").asText("").toLowerCase(Locale.ROOT);
            long expectedBytes = item.path("bytes").asLong(-1);
            String actual = sha256(bytes);
            if (!actual.equals(expected)) {
                throw new PackageException("Хэш " + path + " не совпадает с манифестом: ожидался " + expected + ", получен " + actual);
            }
            if (expectedBytes != bytes.length) {
                throw new PackageException("Размер " + path + " не совпадает с манифестом: " + expectedBytes + " ≠ " + bytes.length);
            }
        }
        for (String name : files.keySet()) {
            if (!listedPaths.contains(name)) throw new PackageException("Лишний файл в пакете, его нет в манифесте: " + name);
        }

        byte[] lessonBytes = files.get("lesson.json");
        if (lessonBytes == null) throw new PackageException("В пакете нет lesson.json");
        JsonNode lesson = parseJson(lessonBytes, "lesson.json");
        String id = lesson.path("id").asText("");
        if (!LESSON_ID.matcher(id).matches()) throw new PackageException("Некорректный id урока в lesson.json: " + id);
        if (lesson.path("title").asText("").isBlank()) throw new PackageException("В lesson.json нет title");
        if (!lesson.path("steps").isArray()) throw new PackageException("В lesson.json нет массива steps");
        if (!lesson.path("screens").isArray()) throw new PackageException("В lesson.json нет массива screens");
        if (!manifest.path("id").asText("").equals(id)) {
            throw new PackageException("id в manifest.json (" + manifest.path("id").asText() + ") не совпадает с lesson.json (" + id + ")");
        }
        for (String ref : referencedPaths(lesson)) {
            if (!files.containsKey(ref)) throw new PackageException("Урок ссылается на отсутствующий файл " + ref);
        }
        return new LessonPackage(manifest, new String(manifestBytes, StandardCharsets.UTF_8), lesson,
                new String(lessonBytes, StandardCharsets.UTF_8), files, sha256(archive));
    }

    public static List<String> referencedPaths(JsonNode lesson) {
        LinkedHashSet<String> paths = new LinkedHashSet<>();
        for (JsonNode screen : lesson.path("screens")) {
            addIfPath(paths, screen.path("html").asText(""));
            addIfPath(paths, screen.path("screenshot").asText(""));
        }
        for (JsonNode step : lesson.path("steps")) {
            addIfPath(paths, step.path("audio").asText(""));
        }
        return new ArrayList<>(paths);
    }

    private static void addIfPath(Set<String> paths, String value) {
        if (value.isEmpty() || value.startsWith("data:")) return;
        paths.add(value);
    }

    static void assertPath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\")) {
            throw new PackageException("Недопустимый путь в пакете: " + path);
        }
        String[] parts = path.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals("..") || part.equals(".")) throw new PackageException("Недопустимый путь в пакете: " + path);
        }
        if (parts.length == 1 ? !ROOT_FILES.contains(path) : !DIRS.contains(parts[0])) {
            throw new PackageException("Файл вне разрешённых каталогов (screens/, original/, assets/, audio/): " + path);
        }
    }

    private Map<String, byte[]> unzip(byte[] archive) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            boolean any = false;
            while ((entry = zip.getNextEntry()) != null) {
                any = true;
                if (entry.isDirectory()) continue;
                String name = entry.getName();
                if (!name.equals("manifest.json")) assertPath(name);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    total += read;
                    if (total > MAX_TOTAL_BYTES) throw new PackageException("Пакет больше " + (MAX_TOTAL_BYTES >> 20) + " МБ после распаковки");
                    out.write(buffer, 0, read);
                }
                if (files.put(name, out.toByteArray()) != null) throw new PackageException("Повтор файла в архиве: " + name);
            }
            if (!any) throw new PackageException("Файл не похож на zip-архив .lesson.zip");
        } catch (ZipException e) {
            throw new PackageException("Повреждённый zip-архив: " + e.getMessage());
        } catch (IOException e) {
            throw new PackageException("Не удалось прочитать архив: " + e.getMessage());
        }
        return files;
    }

    private JsonNode parseJson(byte[] bytes, String name) {
        try {
            JsonNode node = mapper.readTree(bytes);
            if (node == null || !node.isObject()) throw new PackageException(name + " должен быть JSON-объектом");
            return node;
        } catch (IOException e) {
            throw new PackageException(name + " — некорректный JSON: " + e.getMessage());
        }
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
