package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.TimeSource;
import io.jmix.core.security.AccessDeniedException;
import io.uitrainer.platform.entity.StudioRelease;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class StudioReleaseService {

    public static final String WIN = "win";
    private static final Pattern SEMVER = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final Pattern EXE_NAME = Pattern.compile("^[A-Za-z0-9._ -]+\\.exe$");
    private static final String VERSION_HINT = "Версия должна быть X.Y.Z без суффикса";
    private static final String YML_MISMATCH = "latest.yml не соответствует установщику";

    private final DataManager dataManager;
    private final FileStorageLocator fileStorageLocator;
    private final TimeSource timeSource;
    private final CurrentUserAccess access;

    public StudioReleaseService(DataManager dataManager, FileStorageLocator fileStorageLocator,
                                TimeSource timeSource, CurrentUserAccess access) {
        this.dataManager = dataManager;
        this.fileStorageLocator = fileStorageLocator;
        this.timeSource = timeSource;
        this.access = access;
    }

    public Optional<StudioRelease> current(String platform) {
        return dataManager.unconstrained().load(StudioRelease.class)
                .query("select r from uic_StudioRelease r where r.platform = :p")
                .parameter("p", platform)
                .optional();
    }

    /** Поток файла релиза. Закрывает вызывающий. */
    public InputStream open(FileRef ref) {
        try {
            return fileStorageLocator.getDefault().openStream(ref);
        } catch (RuntimeException e) {
            throw new PackageException("Не удалось прочитать файл релиза");
        }
    }

    public byte[] readSmall(FileRef ref) {
        try (InputStream stream = open(ref)) {
            return stream.readAllBytes();
        } catch (IOException e) {
            throw new PackageException("Не удалось прочитать файл релиза");
        }
    }

    /**
     * Загружает релиз под Windows. .exe читается потоком: sha512 и размер считаются по фактическим байтам.
     * Старые файлы удаляются только после коммита, при откате удаляются только что сохранённые новые.
     */
    @Transactional
    public StudioRelease uploadWin(@Nullable String version, @Nullable String exeName, InputStream exe, byte[] latestYml) {
        if (!access.isAdmin()) {
            throw new AccessDeniedException("entity", "uic_StudioRelease", "create");
        }
        FileStorage storage = fileStorageLocator.getDefault();
        List<FileRef> created = new ArrayList<>();
        List<FileRef> replaced = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                List<FileRef> garbage = status == STATUS_COMMITTED ? replaced : created;
                garbage.forEach(ref -> removeQuietly(storage, ref));
            }
        });

        if (exe == null) throw new PackageException("Нужен файл .exe");
        if (latestYml == null || latestYml.length == 0) throw new PackageException("Нужен файл latest.yml");
        String name = basename(exeName);
        ReleaseManifest manifest = parseLatestYml(latestYml);
        if (name == null) {
            name = manifest.path();
        }
        requireExeName(name);
        if (!name.equals(manifest.path()) || !name.equals(manifest.fileUrl())) {
            throw new PackageException(YML_MISMATCH);
        }
        requireSemver(manifest.version());
        if (version != null && !version.isBlank() && !manifest.version().equals(version.trim())) {
            throw new PackageException(YML_MISMATCH);
        }
        String ver = manifest.version();
        Optional<StudioRelease> existing = current(WIN);
        if (existing.isPresent()) {
            requireSemver(existing.get().getVersionName());
            if (compareVersions(ver, existing.get().getVersionName()) <= 0) {
                throw new ConflictException("Текущая версия " + existing.get().getVersionName()
                        + ". Загружаемая " + ver + " не новее — увеличьте версию в desktop/package.json");
            }
        }

        MessageDigest digest = sha512Digest();
        CountingInputStream counting = new CountingInputStream(new DigestInputStream(exe, digest));
        FileRef exeRef = storage.saveStream("studio-win-" + ver + ".exe", counting);
        created.add(exeRef);
        if (counting.count() == 0) throw new PackageException("Нужен файл .exe");
        FileRef ymlRef = storage.saveStream("studio-win-latest.yml", new ByteArrayInputStream(latestYml));
        created.add(ymlRef);

        StudioRelease release = existing.orElseGet(() -> {
            StudioRelease fresh = dataManager.create(StudioRelease.class);
            fresh.setPlatform(WIN);
            return fresh;
        });
        if (existing.isPresent()) {
            if (release.getExe() != null) replaced.add(release.getExe());
            if (release.getLatestYml() != null) replaced.add(release.getLatestYml());
        }
        release.setVersionName(ver);
        release.setFileName(name);
        release.setSha512(Base64.getEncoder().encodeToString(digest.digest()));
        release.setSize(counting.count());
        release.setExe(exeRef);
        release.setLatestYml(ymlRef);
        release.setUploadedAt(timeSource.now().toOffsetDateTime());
        release.setUploadedBy(access.authenticatedUsername());
        return dataManager.unconstrained().save(release);
    }

    public static ReleaseManifest parseLatestYml(byte[] latestYml) {
        Object loaded;
        try {
            loaded = new Yaml().load(new String(latestYml, StandardCharsets.UTF_8));
        } catch (RuntimeException e) {
            throw new PackageException(YML_MISMATCH);
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new PackageException(YML_MISMATCH);
        }
        String ver = string(map.get("version"));
        String path = string(map.get("path"));
        String url = null;
        Object files = map.get("files");
        if (files instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
            url = string(first.get("url"));
        }
        if (ver == null || path == null || url == null) {
            throw new PackageException(YML_MISMATCH);
        }
        requireSemver(ver);
        requireExeName(path);
        requireExeName(url);
        return new ReleaseManifest(ver, path, url);
    }

    public static void requireSemver(String version) {
        if (version == null || !SEMVER.matcher(version).matches()) {
            throw new PackageException(VERSION_HINT);
        }
        String[] parts = version.split("\\.");
        for (String part : parts) {
            try {
                Long.parseLong(part);
            } catch (NumberFormatException e) {
                throw new PackageException(VERSION_HINT);
            }
        }
    }

    /** 1 если a новее b, 0 если равны, -1 если старше. Только после requireSemver. */
    public static int compareVersions(String a, String b) {
        requireSemver(a);
        requireSemver(b);
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        for (int i = 0; i < 3; i++) {
            long x = Long.parseLong(left[i]);
            long y = Long.parseLong(right[i]);
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return 0;
    }

    private static void requireExeName(String name) {
        if (name == null || !EXE_NAME.matcher(name).matches()
                || name.contains("/") || name.contains("\\") || name.contains("..")
                || name.indexOf('"') >= 0) {
            throw new PackageException(YML_MISMATCH);
        }
        for (int i = 0; i < name.length(); i++) {
            if (Character.isISOControl(name.charAt(i))) {
                throw new PackageException(YML_MISMATCH);
            }
        }
    }

    @Nullable
    private static String basename(@Nullable String exeName) {
        if (exeName == null || exeName.isBlank()) {
            return null;
        }
        String name = exeName.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        return name;
    }

    @Nullable
    private static String string(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static void removeQuietly(FileStorage storage, @Nullable FileRef ref) {
        if (ref == null) return;
        try {
            storage.removeFile(ref);
        } catch (RuntimeException ignored) {
            /* файл мог уже отсутствовать */
        }
    }

    private static MessageDigest sha512Digest() {
        try {
            return MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public List<StudioRelease> all() {
        return dataManager.unconstrained().load(StudioRelease.class).all().list();
    }

    public record ReleaseManifest(String version, String path, String fileUrl) {
    }

    /** Считает фактически прочитанные байты. */
    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        long count() {
            return count;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count += skipped;
            return skipped;
        }
    }
}
