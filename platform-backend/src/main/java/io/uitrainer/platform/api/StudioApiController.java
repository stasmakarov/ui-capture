package io.uitrainer.platform.api;

import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * REST для студии.
 * POST /api/lessons/import — .lesson.zip (тело application/zip или multipart поле file), Bearer-токен студии.
 * POST /api/studio/exchange — обмен одноразового тикета на токен студии.
 * GET  /api/studio/whoami — проверка токена.
 */
@RestController
@RequestMapping("/api")
public class StudioApiController {

    private final StudioTokenService tokenService;
    private final LessonImportService importService;
    private final SystemAuthenticator systemAuthenticator;

    public StudioApiController(StudioTokenService tokenService, LessonImportService importService, SystemAuthenticator systemAuthenticator) {
        this.tokenService = tokenService;
        this.importService = importService;
        this.systemAuthenticator = systemAuthenticator;
    }

    @PostMapping(value = "/lessons/import", consumes = {"application/zip", "application/x-zip-compressed", MediaType.APPLICATION_OCTET_STREAM_VALUE})
    public ResponseEntity<Map<String, Object>> importRaw(@RequestBody byte[] body, HttpServletRequest request) {
        return doImport(body, request);
    }

    @PostMapping(value = "/lessons/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importMultipart(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        return doImport(file.getBytes(), request);
    }

    private ResponseEntity<Map<String, Object>> doImport(byte[] body, HttpServletRequest request) {
        Optional<String> username = systemAuthenticator.withSystem(() -> tokenService.authenticate(bearer(request)));
        if (username.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "Нужен действующий токен студии в заголовке Authorization: Bearer …");
        UUID courseId;
        try {
            courseId = courseId(request);
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "Некорректный идентификатор курса");
        }
        try {
            ImportResult result = systemAuthenticator.withUser(username.get(),
                    () -> importService.importPackage(body, courseId, request.getHeader("X-Studio-Version")));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lessonId", result.lesson().getId());
            out.put("externalId", result.lesson().getExternalId());
            out.put("title", result.lesson().getTitle());
            out.put("status", result.lesson().getStatus().getId());
            out.put("versionId", result.version().getId());
            out.put("versionNo", result.version().getVersionNo());
            out.put("newLesson", result.newLesson());
            out.put("url", "/lessons/" + result.lesson().getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(out);
        } catch (PackageException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (AccessException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    public record ExchangeRequest(String ticket, Integer port) {
    }

    @PostMapping("/studio/exchange")
    public ResponseEntity<Map<String, Object>> exchange(@RequestBody ExchangeRequest body) {
        try {
            StudioTokenService.Exchange exchange = systemAuthenticator.withSystem(() ->
                    tokenService.exchangeTicket(body == null ? null : body.ticket(), body == null ? null : body.port()));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("token", exchange.token());
            out.put("username", exchange.username());
            out.put("courseId", exchange.courseId());
            out.put("courseTitle", exchange.courseTitle());
            out.put("expiresAt", exchange.expiresAt() == null ? null : exchange.expiresAt().toString());
            return ResponseEntity.ok(out);
        } catch (AccessException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("/studio/whoami")
    public ResponseEntity<Map<String, Object>> whoami(HttpServletRequest request) {
        Optional<String> username = systemAuthenticator.withSystem(() -> tokenService.authenticate(bearer(request)));
        if (username.isEmpty()) return error(HttpStatus.UNAUTHORIZED, "Токен студии недействителен или отозван");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("username", username.get());
        return ResponseEntity.ok(out);
    }

    private static String bearer(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        return header.substring(7).trim();
    }

    private static UUID courseId(HttpServletRequest request) {
        String value = request.getHeader("X-Course-Id");
        if (value == null || value.isBlank()) value = request.getParameter("course");
        return value == null || value.isBlank() ? null : UUID.fromString(value.trim());
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("error", message);
        out.put("status", status.value());
        return ResponseEntity.status(status).body(out);
    }
}
