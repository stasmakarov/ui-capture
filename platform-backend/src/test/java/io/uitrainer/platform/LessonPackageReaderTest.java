package io.uitrainer.platform;

import io.uitrainer.platform.service.LessonPackage;
import io.uitrainer.platform.service.LessonPackageReader;
import io.uitrainer.platform.service.PackageException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LessonPackageReaderTest {

    private final LessonPackageReader reader = new LessonPackageReader();

    @Test
    void readsValidPackage() {
        LessonPackage pkg = reader.read(TestPackages.validZip("reader-ok", "Урок"));
        assertThat(pkg.lessonId()).isEqualTo("reader-ok");
        assertThat(pkg.files()).containsKeys("lesson.json", "screens/screen-1.html", "original/screen-1.png", "trainer.html");
        assertThat(pkg.files()).doesNotContainKey("manifest.json");
        assertThat(pkg.packageSha256()).hasSize(64);
    }

    @Test
    void rejectsHashMismatch() {
        Map<String, byte[]> files = TestPackages.lessonFiles("reader-hash", "Урок");
        byte[] zip = TestPackages.zip("reader-hash", "Урок", files, "screens/screen-1.html", null);
        assertThatThrownBy(() -> reader.read(zip)).isInstanceOf(PackageException.class)
                .hasMessageContaining("Хэш screens/screen-1.html не совпадает с манифестом");
    }

    @Test
    void rejectsFileNotInManifest() {
        Map<String, byte[]> files = TestPackages.lessonFiles("reader-extra", "Урок");
        byte[] zip = TestPackages.zip("reader-extra", "Урок", files, null, "assets/evil.js");
        assertThatThrownBy(() -> reader.read(zip)).isInstanceOf(PackageException.class).hasMessageContaining("Лишний файл");
    }

    @Test
    void rejectsMissingReferencedFile() {
        Map<String, byte[]> files = TestPackages.lessonFiles("reader-missing", "Урок");
        files.remove("original/screen-1.png");
        byte[] zip = TestPackages.zip("reader-missing", "Урок", files);
        assertThatThrownBy(() -> reader.read(zip)).isInstanceOf(PackageException.class)
                .hasMessageContaining("отсутствующий файл original/screen-1.png");
    }

    @Test
    void rejectsPathTraversalAndGarbage() {
        Map<String, byte[]> files = TestPackages.lessonFiles("reader-path", "Урок");
        files.put("screens/../../etc/passwd", "x".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> reader.read(TestPackages.zip("reader-path", "Урок", files)))
                .isInstanceOf(PackageException.class).hasMessageContaining("Недопустимый путь");
        assertThatThrownBy(() -> reader.read("not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PackageException.class);
        assertThatThrownBy(() -> reader.read(new byte[0])).isInstanceOf(PackageException.class);
    }

    @Test
    void rejectsManifestIdMismatch() {
        Map<String, byte[]> files = TestPackages.lessonFiles("reader-a", "Урок");
        byte[] zip = TestPackages.zip("reader-b", "Урок", files);
        assertThatThrownBy(() -> reader.read(zip)).isInstanceOf(PackageException.class).hasMessageContaining("не совпадает с lesson.json");
    }
}
