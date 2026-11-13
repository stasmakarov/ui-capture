package io.uitrainer.platform;

import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.service.AppVersionService;
import io.uitrainer.platform.service.StudioReleaseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;

import java.time.Instant;
import java.util.Optional;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AppVersionServiceTest {

    @Test
    void withBuildAndGit() {
        Properties build = new Properties();
        build.setProperty("version", "0.7.0");
        build.setProperty("jmixVersion", "2.8.6");
        build.setProperty("time", Instant.parse("2026-01-01T00:00:00Z").toString());
        Properties git = new Properties();
        git.setProperty("branch", "main");
        git.setProperty("commit.id", "abc1234def567");
        git.setProperty("commit.id.abbrev", "abc1234");
        git.setProperty("commit.time", "2026-11-11T10:00:00Z");
        AppVersionService service = new AppVersionService(
                provider(new BuildProperties(build)),
                provider(new GitProperties(git)),
                studio("0.6.2"));
        AppVersionService.Info info = service.current();
        assertThat(service.platformVersion()).isEqualTo("0.7.0");
        assertThat(info.version()).isEqualTo("0.7.0");
        assertThat(info.commit()).isEqualTo("abc1234");
        assertThat(info.branch()).isEqualTo("main");
        assertThat(info.date()).isNotNull();
        assertThat(info.jmixVersion()).isEqualTo("2.8.6");
        assertThat(info.apiVersion()).isEqualTo(1);
        assertThat(info.studioVersion()).isEqualTo("0.6.2");
    }

    @Test
    void withoutBuildPropertiesFallsBack() {
        AppVersionService service = new AppVersionService(provider(null), provider(null), studio(null));
        assertThat(service.platformVersion()).isEqualTo("0.6.0");
        AppVersionService.Info info = service.current();
        assertThat(info.version()).isEqualTo("0.6.0");
        assertThat(info.commit()).isNull();
        assertThat(info.branch()).isNull();
        assertThat(info.date()).isNull();
        assertThat(info.jmixVersion()).isNull();
        assertThat(info.studioVersion()).isNull();
    }

    @Test
    void withoutGitUsesBuildTime() {
        Properties build = new Properties();
        build.setProperty("version", "0.6.0");
        build.setProperty("time", Instant.parse("2026-02-02T12:00:00Z").toString());
        AppVersionService service = new AppVersionService(
                provider(new BuildProperties(build)),
                provider(null),
                studio(null));
        AppVersionService.Info info = service.current();
        assertThat(info.commit()).isNull();
        assertThat(info.date()).isEqualTo(Instant.parse("2026-02-02T12:00:00Z"));
    }

    private static StudioReleaseService studio(String version) {
        StudioReleaseService releases = mock(StudioReleaseService.class);
        Optional<StudioRelease> current = Optional.empty();
        if (version != null) {
            StudioRelease release = new StudioRelease();
            release.setVersionName(version);
            current = Optional.of(release);
        }
        when(releases.current(StudioReleaseService.WIN)).thenReturn(current);
        return releases;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
