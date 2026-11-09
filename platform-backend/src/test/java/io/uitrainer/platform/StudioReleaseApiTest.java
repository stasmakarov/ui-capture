package io.uitrainer.platform;

import io.jmix.core.FileRef;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;
import io.uitrainer.platform.service.StudioTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StudioReleaseApiTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    StudioTokenService tokenService;
    @Autowired
    SystemAuthenticator systemAuthenticator;
    @Autowired
    DemoDataInitializer demoData;
    @Autowired
    StudioReleaseService releases;
    @Autowired
    FileStorageLocator fileStorageLocator;

    /** База тестов живёт между запусками, поэтому версия каждый раз новая. */
    private static String uniqueVersion() {
        return "9.9." + System.nanoTime();
    }

    private static String exeName(String version) {
        return "studio-Setup-" + version + ".exe";
    }

    private static byte[] latestYml(String version) {
        return ("version: " + version + "\npath: " + exeName(version) + "\nfiles:\n  - url: " + exeName(version) + "\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String adminToken() {
        demoData.init();
        return systemAuthenticator.withUser("admin", () -> tokenService.issueToken("rel", null).plainText());
    }

    private StudioRelease current() {
        return systemAuthenticator.withSystem(() -> releases.current(StudioReleaseService.WIN).orElseThrow());
    }

    @Test
    void versionIsPublic() throws Exception {
        mvc.perform(get("/api/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiVersion").value(1))
                .andExpect(jsonPath("$.platformVersion").exists());
    }

    @Test
    void uploadRequiresAdminStudioToken() throws Exception {
        demoData.init();
        String version = uniqueVersion();
        byte[] exe = "exe-bytes".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile exePart = new MockMultipartFile("exe", exeName(version), "application/octet-stream", exe);
        MockMultipartFile ymlPart = new MockMultipartFile("latestYml", "latest.yml", "text/yaml", latestYml(version));

        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", version))
                .andExpect(status().isUnauthorized());

        String trainer = systemAuthenticator.withUser("trainer", () -> tokenService.issueToken("t", null).plainText());
        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", version)
                        .header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());

        String admin = adminToken();
        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", version)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value(version));

        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", version)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/studio/releases/win/latest.yml")).andExpect(status().isOk());
        mvc.perform(get("/api/studio/releases/win/" + exeName(version))).andExpect(status().isOk());
        mvc.perform(get("/api/studio/releases/latest")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(version));
    }

    @Test
    void latestYmlMustMatchVersionAndExe() throws Exception {
        String version = uniqueVersion();
        MockMultipartFile exePart = new MockMultipartFile("exe", exeName(version), "application/octet-stream", new byte[]{1, 2, 3});
        MockMultipartFile ymlPart = new MockMultipartFile("latestYml", "latest.yml", "text/yaml", latestYml(uniqueVersion()));
        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", version)
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void newReleaseReplacesOldFilesAfterCommitAndStreamsExe() throws Exception {
        String admin = adminToken();
        String first = uniqueVersion();
        mvc.perform(multipart("/api/studio/releases/win")
                        .file(new MockMultipartFile("exe", exeName(first), "application/octet-stream", new byte[]{7, 7}))
                        .file(new MockMultipartFile("latestYml", "latest.yml", "text/yaml", latestYml(first)))
                        .param("version", first).header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());
        StudioRelease old = current();
        FileRef oldExe = old.getExe();
        FileRef oldYml = old.getLatestYml();

        String second = uniqueVersion();
        byte[] exe = new byte[300_000];
        for (int i = 0; i < exe.length; i++) exe[i] = (byte) (i * 31);
        mvc.perform(multipart("/api/studio/releases/win")
                        .file(new MockMultipartFile("exe", exeName(second), "application/octet-stream", exe))
                        .file(new MockMultipartFile("latestYml", "latest.yml", "text/yaml", latestYml(second)))
                        .param("version", second).header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());

        StudioRelease fresh = current();
        assertThat(fresh.getVersionName()).isEqualTo(second);
        assertThat(fresh.getSize()).isEqualTo(exe.length);
        assertThat(fresh.getSha512()).isEqualTo(Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-512").digest(exe)));
        var storage = fileStorageLocator.getDefault();
        assertThat(storage.fileExists(oldExe)).isFalse();
        assertThat(storage.fileExists(oldYml)).isFalse();
        assertThat(storage.fileExists(fresh.getExe())).isTrue();

        MvcResult download = mvc.perform(get("/api/studio/releases/win/" + exeName(second)))
                .andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", exe.length))
                .andReturn();
        assertThat(download.getResponse().getContentAsByteArray()).isEqualTo(exe);
        mvc.perform(get("/api/studio/releases/win/" + exeName(first))).andExpect(status().isNotFound());
    }

    @Test
    void failedUploadKeepsCurrentReleaseAndRemovesNewFiles() throws Exception {
        String admin = adminToken();
        String version = uniqueVersion();
        mvc.perform(multipart("/api/studio/releases/win")
                        .file(new MockMultipartFile("exe", exeName(version), "application/octet-stream", new byte[]{1}))
                        .file(new MockMultipartFile("latestYml", "latest.yml", "text/yaml", latestYml(version)))
                        .param("version", version).header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());
        StudioRelease before = current();
        long filesBefore = countStoredFiles();

        String next = uniqueVersion();
        assertThatThrownBy(() -> systemAuthenticator.withSystem(() -> releases.uploadWin(next, exeName(next),
                new ByteArrayInputStream(new byte[0]), latestYml(next), "admin")))
                .isInstanceOf(PackageException.class);

        StudioRelease after = current();
        assertThat(after.getVersionName()).isEqualTo(before.getVersionName());
        var storage = fileStorageLocator.getDefault();
        assertThat(storage.fileExists(after.getExe())).isTrue();
        assertThat(storage.fileExists(after.getLatestYml())).isTrue();
        assertThat(countStoredFiles()).isEqualTo(filesBefore);
    }

    private static long countStoredFiles() throws Exception {
        java.nio.file.Path root = java.nio.file.Path.of("build/test-files");
        if (!java.nio.file.Files.exists(root)) return 0;
        try (var walk = java.nio.file.Files.walk(root)) {
            return walk.filter(java.nio.file.Files::isRegularFile).count();
        }
    }
}
