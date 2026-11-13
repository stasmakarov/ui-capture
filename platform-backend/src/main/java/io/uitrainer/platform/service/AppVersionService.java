package io.uitrainer.platform.service;

import io.uitrainer.platform.api.ApiVersion;
import io.uitrainer.platform.entity.StudioRelease;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class AppVersionService {

    public static final String FALLBACK_VERSION = "0.6.0";

    private final ObjectProvider<BuildProperties> buildProperties;
    private final ObjectProvider<GitProperties> gitProperties;
    private final StudioReleaseService releases;

    public AppVersionService(ObjectProvider<BuildProperties> buildProperties,
                             ObjectProvider<GitProperties> gitProperties,
                             StudioReleaseService releases) {
        this.buildProperties = buildProperties;
        this.gitProperties = gitProperties;
        this.releases = releases;
    }

    public String platformVersion() {
        BuildProperties build = buildProperties.getIfAvailable();
        if (build != null && hasText(build.getVersion())) {
            return build.getVersion();
        }
        return FALLBACK_VERSION;
    }

    public Info current() {
        BuildProperties build = buildProperties.getIfAvailable();
        GitProperties git = gitProperties.getIfAvailable();
        String commit = git != null ? git.getShortCommitId() : null;
        String branch = git != null ? git.getBranch() : null;
        Instant date = build != null ? build.getTime() : null;
        if (date == null && git != null) {
            date = git.getCommitTime();
        }
        String jmix = build != null ? build.get("jmixVersion") : null;
        String studio = releases.current(StudioReleaseService.WIN)
                .map(StudioRelease::getVersionName)
                .orElse(null);
        return new Info(
                platformVersion(),
                blankToNull(commit),
                blankToNull(branch),
                date,
                blankToNull(jmix),
                ApiVersion.CURRENT,
                studio);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Nullable
    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    public record Info(
            String version,
            @Nullable String commit,
            @Nullable String branch,
            @Nullable Instant date,
            @Nullable String jmixVersion,
            int apiVersion,
            @Nullable String studioVersion) {
    }
}
