package io.uitrainer.platform.view.studio;

import com.vaadin.flow.component.html.Anchor;
import io.uitrainer.platform.entity.StudioRelease;

/** Ссылка на REST-скачивание установщика: мимо роутера Vaadin. */
public final class StudioDownload {
    private StudioDownload() {
    }

    public static Anchor link(StudioRelease release) {
        Anchor download = new Anchor("/api/studio/releases/win/" + release.getFileName(), "Скачать студию");
        download.setId("downloadStudio");
        download.setRouterIgnore(true);
        download.getElement().setAttribute("download", true);
        return download;
    }
}
