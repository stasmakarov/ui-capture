package io.uitrainer.platform.view.studio;

import io.jmix.core.DataManager;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.service.CurrentUserAccess;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/** Адрес локальной студии: порт пользователя или {@code uitrainer.studio.local-url}. */
@Component
public class StudioLocalUrl {

    private final DataManager dataManager;
    private final CurrentUserAccess access;
    private final String defaultUrl;

    public StudioLocalUrl(DataManager dataManager,
                          CurrentUserAccess access,
                          @Value("${uitrainer.studio.local-url:http://127.0.0.1:44731}") String defaultUrl) {
        this.dataManager = dataManager;
        this.access = access;
        this.defaultUrl = defaultUrl;
    }

    public String resolve() {
        try {
            User user = dataManager.unconstrained().load(User.class)
                    .query("select u from User u where u.username = :u")
                    .parameter("u", access.username())
                    .one();
            return fromPort(user.getStudioLocalPort(), defaultUrl);
        } catch (RuntimeException ignored) {
            return defaultUrl;
        }
    }

    static String fromPort(@Nullable Integer port, String defaultUrl) {
        if (port != null) return "http://127.0.0.1:" + port;
        return defaultUrl;
    }
}
