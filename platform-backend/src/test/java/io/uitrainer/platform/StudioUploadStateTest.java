package io.uitrainer.platform;

import io.uitrainer.platform.service.StudioUploadState;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StudioUploadStateTest {

    @Test
    void missingFiles() {
        assertThat(StudioUploadState.of(false, null, null).enabled()).isFalse();
        assertThat(StudioUploadState.of(false, null, null).reason()).isEqualTo("Выберите оба файла");
        assertThat(StudioUploadState.of(true, null, null).reason()).isEqualTo("Выберите оба файла");
        assertThat(StudioUploadState.of(false, yml("0.6.3"), null).reason()).isEqualTo("Выберите оба файла");
    }

    @Test
    void newerThanServerIsEnabled() {
        StudioUploadState.Decision d = StudioUploadState.of(true, yml("0.6.3"), "0.6.2");
        assertThat(d.enabled()).isTrue();
        assertThat(d.reason()).isEmpty();
    }

    @Test
    void firstReleaseWithValidSemverIsEnabled() {
        assertThat(StudioUploadState.of(true, yml("0.6.2"), null).enabled()).isTrue();
        assertThat(StudioUploadState.of(true, yml("0.6.2"), "").enabled()).isTrue();
    }

    @Test
    void equalVersionIsDisabled() {
        StudioUploadState.Decision d = StudioUploadState.of(true, yml("0.6.2"), "0.6.2");
        assertThat(d.enabled()).isFalse();
        assertThat(d.reason()).isEqualTo("Версия 0.6.2 уже загружена");
    }

    @Test
    void olderVersionIsDisabled() {
        StudioUploadState.Decision d = StudioUploadState.of(true, yml("0.6.1"), "0.6.2");
        assertThat(d.enabled()).isFalse();
        assertThat(d.reason()).isEqualTo("Версия 0.6.1 старше загруженной 0.6.2");
    }

    @Test
    void brokenYmlIsDisabled() {
        StudioUploadState.Decision d = StudioUploadState.of(true, "not yaml".getBytes(StandardCharsets.UTF_8), null);
        assertThat(d.enabled()).isFalse();
        assertThat(d.reason()).isNotBlank();
    }

    @Test
    void afterResetLooksLikeNoFiles() {
        StudioUploadState.Decision loaded = StudioUploadState.of(true, yml("0.7.0"), "0.6.2");
        assertThat(loaded.enabled()).isTrue();
        StudioUploadState.Decision reset = StudioUploadState.of(false, null, "0.7.0");
        assertThat(reset.enabled()).isFalse();
        assertThat(reset.reason()).isEqualTo("Выберите оба файла");
    }

    private static byte[] yml(String version) {
        String name = "studio-Setup-" + version + ".exe";
        return ("version: " + version + "\npath: " + name + "\nfiles:\n  - url: " + name + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }
}
