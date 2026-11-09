package io.uitrainer.platform;

import io.jmix.core.security.SystemAuthenticator;
import io.uitrainer.platform.app.DemoDataInitializer;
import io.uitrainer.platform.service.StudioTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        byte[] exe = "exe-bytes".getBytes(StandardCharsets.UTF_8);
        byte[] yml = """
                version: 9.9.9
                path: studio-Setup-9.9.9.exe
                files:
                  - url: studio-Setup-9.9.9.exe
                """.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile exePart = new MockMultipartFile("exe", "studio-Setup-9.9.9.exe", "application/octet-stream", exe);
        MockMultipartFile ymlPart = new MockMultipartFile("latestYml", "latest.yml", "text/yaml", yml);

        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", "9.9.9"))
                .andExpect(status().isUnauthorized());

        String trainer = systemAuthenticator.withUser("trainer", () -> tokenService.issueToken("t", null).plainText());
        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", "9.9.9")
                        .header("Authorization", "Bearer " + trainer))
                .andExpect(status().isForbidden());

        String admin = systemAuthenticator.withUser("admin", () -> tokenService.issueToken("rel", null).plainText());
        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", "9.9.9")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").value("9.9.9"));

        mvc.perform(multipart("/api/studio/releases/win").file(exePart).file(ymlPart).param("version", "9.9.9")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/studio/releases/win/latest.yml")).andExpect(status().isOk());
        mvc.perform(get("/api/studio/releases/win/studio-Setup-9.9.9.exe")).andExpect(status().isOk());
        mvc.perform(get("/api/studio/releases/latest")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value("9.9.9"));
    }
}
