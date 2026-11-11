package io.uitrainer.platform.api;

import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.security.AdminRole;
import io.uitrainer.platform.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/studio/releases")
public class StudioReleaseController {

    private final StudioReleaseService releases;
    private final StudioTokenService tokenService;
    private final UserProvisioning provisioning;
    private final SystemAuthenticator systemAuthenticator;

    public StudioReleaseController(StudioReleaseService releases, StudioTokenService tokenService,
                                   UserProvisioning provisioning, SystemAuthenticator systemAuthenticator) {
        this.releases = releases;
        this.tokenService = tokenService;
        this.provisioning = provisioning;
        this.systemAuthenticator = systemAuthenticator;
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        return releases.current(StudioReleaseService.WIN)
                .map(release -> ResponseEntity.ok(VersionController.summary(release)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(error(HttpStatus.NOT_FOUND, "Релиз студии ещё не загружен")));
    }

    @GetMapping(value = "/win/latest.yml", produces = "text/yaml")
    public ResponseEntity<byte[]> latestYml() {
        Optional<StudioRelease> current = releases.current(StudioReleaseService.WIN);
        if (current.isEmpty()) return ResponseEntity.notFound().build();
        byte[] body = releases.readSmall(current.get().getLatestYml());
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "yaml", StandardCharsets.UTF_8))
                .body(body);
    }

    @GetMapping("/win/{fileName:.+}")
    public ResponseEntity<Resource> winFile(@PathVariable String fileName) {
        Optional<StudioRelease> current = releases.current(StudioReleaseService.WIN);
        if (current.isEmpty() || !current.get().getFileName().equals(fileName)) return ResponseEntity.notFound().build();
        StudioRelease release = current.get();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + release.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(release.getSize())
                .body(new InputStreamResource(releases.open(release.getExe())));
    }

    @PostMapping(value = "/win", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam(value = "version", required = false) String version,
            @RequestParam("exe") MultipartFile exe,
            @RequestParam("latestYml") MultipartFile latestYml,
            HttpServletRequest request) throws IOException {
        Optional<String> username = systemAuthenticator.withSystem(() -> tokenService.authenticate(bearer(request)));
        if (username.isEmpty()) return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(error(HttpStatus.UNAUTHORIZED, "Нужен админский токен студии"));
        boolean admin = systemAuthenticator.withSystem(() -> provisioning.resourceRoles(username.get()).contains(AdminRole.CODE));
        if (!admin) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error(HttpStatus.FORBIDDEN, "Загрузка релиза доступна только администратору"));
        try {
            byte[] yml = latestYml.getBytes();
            StudioRelease saved = systemAuthenticator.withSystem(() -> {
                try (InputStream exeStream = exe.getInputStream()) {
                    return releases.uploadWin(version, exe.getOriginalFilename(), exeStream, yml, username.get());
                } catch (IOException e) {
                    throw new PackageException("Не удалось прочитать файлы");
                }
            });
            return ResponseEntity.status(HttpStatus.CREATED).body(VersionController.summary(saved));
        } catch (ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(error(HttpStatus.CONFLICT, e.getMessage()));
        } catch (PackageException e) {
            return ResponseEntity.badRequest().body(error(HttpStatus.BAD_REQUEST, e.getMessage()));
        }
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return header.substring(7).trim();
    }

    private static Map<String, Object> error(HttpStatus status, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", message);
        out.put("status", status.value());
        return out;
    }
}
