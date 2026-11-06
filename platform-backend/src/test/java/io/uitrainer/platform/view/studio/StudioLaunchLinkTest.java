package io.uitrainer.platform.view.studio;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudioLaunchLinkTest {

    @Test
    void studioLinkBindsCourseServerAndTicket() {
        assertThat(StudioLaunch.studioLink("http://localhost:8090", "uictk_abc", "6f0c"))
                .isEqualTo("uitrainer://new?course=6f0c&server=http%3A%2F%2Flocalhost%3A8090&ticket=uictk_abc");
    }

    @Test
    void missingCourseStaysAnEmptyParameter() {
        assertThat(StudioLaunch.studioLink("http://localhost:8090", "uictk_abc", ""))
                .startsWith("uitrainer://new?course=&server=");
    }
}
