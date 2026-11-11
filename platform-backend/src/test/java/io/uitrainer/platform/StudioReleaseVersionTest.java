package io.uitrainer.platform;

import io.uitrainer.platform.service.StudioReleaseService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudioReleaseVersionTest {

    @Test
    void comparesSemverByNumericSegments() {
        assertThat(StudioReleaseService.compareVersions("0.10.0", "0.9.0")).isPositive();
        assertThat(StudioReleaseService.compareVersions("0.9.0", "0.10.0")).isNegative();
        assertThat(StudioReleaseService.compareVersions("0.6.1", "0.6.1")).isZero();
        assertThat(StudioReleaseService.compareVersions("1.0.0", "0.9.9")).isPositive();
    }

    @Test
    void readsVersionFromYml() {
        assertThat(StudioReleaseService.ymlValue("version: 0.6.1\npath: a.exe\n", "version:")).isEqualTo("0.6.1");
    }
}
