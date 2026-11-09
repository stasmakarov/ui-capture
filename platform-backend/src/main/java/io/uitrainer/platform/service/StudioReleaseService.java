package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.StudioRelease;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

@Service
public class StudioReleaseService {

    public static final String WIN = "win";

    private final DataManager dataManager;
    private final FileStorageLocator fileStorageLocator;
    private final TimeSource timeSource;

    public StudioReleaseService(DataManager dataManager, FileStorageLocator fileStorageLocator, TimeSource timeSource) {
        this.dataManager = dataManager;
        this.fileStorageLocator = fileStorageLocator;
        this.timeSource = timeSource;
    }

    public Optional<StudioRelease> current(String platform) {
        return dataManager.unconstrained().load(StudioRelease.class)
                .query("select r from uic_StudioRelease r where r.platform = :p")
                .parameter("p", platform)
                .optional();
    }

    public byte[] open(FileRef ref) {
        try (var stream = fileStorageLocator.getDefault().openStream(ref)) {
            return stream.readAllBytes();
        } catch (Exception e) {
            throw new PackageException("Не удалось прочитать файл релиза");
        }
    }

    @Transactional
    public StudioRelease uploadWin(String version, String exeName, byte[] exe, byte[] latestYml, String uploadedBy) {
        if (version == null || version.isBlank()) throw new PackageException("Укажите version");
        if (exe == null || exe.length == 0) throw new PackageException("Нужен файл .exe");
        if (latestYml == null || latestYml.length == 0) throw new PackageException("Нужен файл latest.yml");
        String name = exeName == null || exeName.isBlank() ? "uitrainer-Studio-Setup-" + version.trim() + ".exe" : exeName;
        if (!name.toLowerCase().endsWith(".exe")) throw new PackageException("Ожидается файл .exe");
        String yml = new String(latestYml, StandardCharsets.UTF_8);
        if (!ymlContains(yml, "version:", version.trim()) || !yml.contains(name)) {
            throw new PackageException("latest.yml должен ссылаться на загруженный .exe и совпадать по version");
        }
        Optional<StudioRelease> existing = current(WIN);
        if (existing.isPresent() && version.trim().equals(existing.get().getVersionName())) {
            throw new ConflictException("Релиз этой версии уже загружен");
        }
        FileStorage storage = fileStorageLocator.getDefault();
        StudioRelease release = existing.orElseGet(() -> {
            StudioRelease created = dataManager.create(StudioRelease.class);
            created.setPlatform(WIN);
            return created;
        });
        if (existing.isPresent()) {
            removeQuietly(storage, release.getExe());
            removeQuietly(storage, release.getLatestYml());
        }
        release.setVersionName(version.trim());
        release.setFileName(name);
        release.setSha512(sha512(exe));
        release.setSize((long) exe.length);
        release.setExe(storage.saveStream("studio-win-" + version.trim() + ".exe", new ByteArrayInputStream(exe)));
        release.setLatestYml(storage.saveStream("studio-win-latest.yml", new ByteArrayInputStream(latestYml)));
        release.setUploadedAt(timeSource.now().toOffsetDateTime());
        release.setUploadedBy(uploadedBy);
        return dataManager.unconstrained().save(release);
    }

    private static void removeQuietly(FileStorage storage, @Nullable FileRef ref) {
        if (ref == null) return;
        try {
            storage.removeFile(ref);
        } catch (RuntimeException ignored) {
            /* файл мог уже отсутствовать */
        }
    }

    private static boolean ymlContains(String yml, String key, String value) {
        for (String line : yml.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key)) {
                String rest = trimmed.substring(key.length()).trim().replace("\"", "").replace("'", "");
                if (rest.equals(value)) return true;
            }
        }
        return false;
    }

    private static String sha512(byte[] bytes) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<StudioRelease> all() {
        return dataManager.unconstrained().load(StudioRelease.class).all().list();
    }
}
