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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
    public StudioRelease uploadWin(String version, @Nullable String exeName, InputStream exe, byte[] latestYml, String uploadedBy) {
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
        String yml = new String(latestYml, StandardCharsets.UTF_8);
        String ver = ymlValue(yml, "version:");
        if (ver == null || ver.isBlank()) throw new PackageException("В latest.yml нет version");
        if (ver.contains("-")) throw new PackageException("Пре-релизы не принимаются. Укажите версию без суффикса в desktop/package.json");
        if (version != null && !version.isBlank() && !ver.equals(version.trim())) {
            throw new PackageException("latest.yml должен ссылаться на загруженный .exe и совпадать по version");
        }
        String name = exeName == null || exeName.isBlank() ? "uitrainer-Studio-Setup-" + ver + ".exe" : exeName;
        if (!name.toLowerCase().endsWith(".exe")) throw new PackageException("Ожидается файл .exe");
        if (!yml.contains(name)) {
            throw new PackageException("latest.yml должен ссылаться на загруженный .exe и совпадать по version");
        }
        Optional<StudioRelease> existing = current(WIN);
        if (existing.isPresent() && compareVersions(ver, existing.get().getVersionName()) <= 0) {
            throw new ConflictException("Текущая версия " + existing.get().getVersionName()
                    + ". Загружаемая " + ver + " не новее — увеличьте версию в desktop/package.json");
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

    public static String ymlValue(String yml, String key) {
        for (String line : yml.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key)) {
                return trimmed.substring(key.length()).trim().replace("\"", "").replace("'", "");
            }
        }
        return null;
    }

    /** 1 если a новее b, 0 если равны, -1 если старше. Сегменты — целые числа. */
    public static int compareVersions(String a, String b) {
        String[] left = a.split("\\.");
        String[] right = b.split("\\.");
        int n = Math.max(left.length, right.length);
        for (int i = 0; i < n; i++) {
            long x = i < left.length ? parseSegment(left[i]) : 0;
            long y = i < right.length ? parseSegment(right[i]) : 0;
            if (x != y) {
                return Long.compare(x, y);
            }
        }
        return 0;
    }

    private static long parseSegment(String part) {
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException e) {
            return 0;
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
