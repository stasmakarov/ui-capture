package io.uitrainer.platform.view.studio;

import com.vaadin.flow.component.html.Anchor;
import io.uitrainer.platform.entity.StudioRelease;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Ссылка на REST-скачивание установщика: мимо роутера Vaadin. */
public final class StudioDownload {
    private StudioDownload() {
    }

    public static Anchor link(StudioRelease release) {
        return link(release, "Скачать студию");
    }

    public static Anchor link(StudioRelease release, String caption) {
        String encoded = URLEncoder.encode(release.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        Anchor download = new Anchor("/api/studio/releases/win/" + encoded, caption);
        download.setId("downloadStudio");
        download.setRouterIgnore(true);
        download.getElement().setAttribute("download", true);
        return download;
    }
}
