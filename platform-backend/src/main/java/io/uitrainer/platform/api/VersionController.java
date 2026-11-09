package io.uitrainer.platform.api;

import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.service.StudioReleaseService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class VersionController {

    private final StudioReleaseService releases;
    @Nullable
    private final BuildProperties buildProperties;

    public VersionController(StudioReleaseService releases, ObjectProvider<BuildProperties> buildProperties) {
        this.releases = releases;
        this.buildProperties = buildProperties.getIfAvailable();
    }

    @GetMapping("/version")
    public ResponseEntity<Map<String, Object>> version() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("platformVersion", buildProperties == null ? "0.6.0" : buildProperties.getVersion());
        out.put("apiVersion", ApiVersion.CURRENT);
        out.put("minStudioApi", ApiVersion.CURRENT);
        out.put("studioLatestRelease", studioLatest());
        return ResponseEntity.ok(out);
    }

    @Nullable
    private Map<String, Object> studioLatest() {
        return releases.current(StudioReleaseService.WIN).map(VersionController::summary).orElse(null);
    }

    static Map<String, Object> summary(StudioRelease release) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("version", release.getVersionName());
        out.put("url", "/api/studio/releases/win/" + release.getFileName());
        out.put("uploadedAt", release.getUploadedAt() == null ? null : release.getUploadedAt().toString());
        return out;
    }
}
