package io.uitrainer.platform.view.studio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudioLocalUrlTest {

    @Test
    void userPortWinsOverDefault() {
        assertThat(StudioLocalUrl.fromPort(44740, "http://127.0.0.1:44731"))
                .isEqualTo("http://127.0.0.1:44740");
    }

    @Test
    void missingPortUsesConfiguredUrl() {
        assertThat(StudioLocalUrl.fromPort(null, "http://127.0.0.1:44731"))
                .isEqualTo("http://127.0.0.1:44731");
    }
}
