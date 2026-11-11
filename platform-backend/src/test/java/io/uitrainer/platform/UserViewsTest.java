package io.uitrainer.platform;

import io.jmix.flowui.view.ViewRegistry;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class UserViewsTest {

    @Autowired
    ViewRegistry viewRegistry;

    @Test
    void primaryUserViewsAreUicIds() {
        assertThat(viewRegistry.getListViewInfo(User.class).getId()).isEqualTo(PlatformViews.USER_LIST);
        assertThat(viewRegistry.getDetailViewInfo(User.class).getId()).isEqualTo(PlatformViews.USER_DETAIL);
    }

    @Test
    void userListHasSubstitutionAction() throws Exception {
        byte[] xml = getClass().getResourceAsStream("/io/uitrainer/platform/view/user/user-list-view.xml")
                .readAllBytes();
        assertThat(new String(xml, StandardCharsets.UTF_8)).contains("type=\"sec_showUserSubstitutions\"");
    }
}
