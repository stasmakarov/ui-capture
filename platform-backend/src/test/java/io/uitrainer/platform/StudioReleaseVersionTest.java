package io.uitrainer.platform;

import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StudioReleaseVersionTest {

    @Test
    void comparesSemverByNumericSegments() {
        assertThat(StudioReleaseService.compareVersions("0.6.1", "0.6.0")).isPositive();
        assertThat(StudioReleaseService.compareVersions("0.10.0", "0.9.0")).isPositive();
        assertThat(StudioReleaseService.compareVersions("0.9.0", "0.10.0")).isNegative();
        assertThat(StudioReleaseService.compareVersions("0.6.1", "0.6.1")).isZero();
        assertThat(StudioReleaseService.compareVersions("1.0.0", "0.9.9")).isPositive();
    }

    @Test
    void rejectsNonStrictSemver() {
        for (String bad : new String[]{"1.0", "1.0.0-beta", "1.x.0", "01.0.0a", "0.5"}) {
            assertThatThrownBy(() -> StudioReleaseService.requireSemver(bad))
                    .isInstanceOf(PackageException.class)
                    .hasMessageContaining("X.Y.Z");
            assertThatThrownBy(() -> StudioReleaseService.compareVersions(bad, "1.0.0"))
                    .isInstanceOf(PackageException.class);
        }
    }

    @Test
    void parseLatestYmlReadsVersionPathAndUrl() {
        byte[] yml = """
                version: 0.6.1
                path: a.exe
                files:
                  - url: a.exe
                """.getBytes(StandardCharsets.UTF_8);
        StudioReleaseService.ReleaseManifest manifest = StudioReleaseService.parseLatestYml(yml);
        assertThat(manifest.version()).isEqualTo("0.6.1");
        assertThat(manifest.path()).isEqualTo("a.exe");
        assertThat(manifest.fileUrl()).isEqualTo("a.exe");
    }

    @Test
    void parseLatestYmlRejectsPathTraversalAndCommentOnlyName() {
        assertThatThrownBy(() -> StudioReleaseService.parseLatestYml("""
                version: 0.6.1
                path: ../evil.exe
                files:
                  - url: ../evil.exe
                """.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("latest.yml не соответствует установщику");
        assertThatThrownBy(() -> StudioReleaseService.parseLatestYml("""
                # studio-Setup-0.6.1.exe
                version: 0.6.1
                """.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(PackageException.class)
                .hasMessageContaining("latest.yml не соответствует установщику");
    }
}
