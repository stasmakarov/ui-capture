package io.uitrainer.platform.view.studio;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;

/**
 * Блок кабинета: спрашивает локальную студию {@code GET /api/presence} из браузера.
 */
public class StudioPresence extends VerticalLayout {

    private static final String PROBE_JS = """
            const studio = $0;
            const ctrl = new AbortController();
            const timer = setTimeout(() => ctrl.abort(), 1500);
            return fetch(studio + '/api/presence', { cache: 'no-store', signal: ctrl.signal })
              .then(async (response) => {
                if (!response.ok) return 'missing';
                const body = await response.json();
                if (body.app !== 'uitrainer-studio' || !body.studioVersion) return 'missing';
                return 'ok:' + body.studioVersion;
              })
              .catch(() => 'missing')
              .finally(() => clearTimeout(timer));
            """;

    private final StudioRelease release;
    private final Span status = new Span("Проверяем студию…");

    public StudioPresence(StudioRelease release, String localUrl) {
        this.release = release;
        setPadding(false);
        setSpacing(true);
        status.setId("studioPresenceStatus");
        add(status);
        addAttachListener(event -> event.getUI().getPage().executeJs(PROBE_JS, localUrl)
                .then(String.class, this::applyResult));
    }

    private void applyResult(String result) {
        removeAll();
        add(status);
        if (result != null && result.startsWith("ok:")) {
            String version = result.substring(3);
            status.setText("Студия " + version + " установлена на этом компьютере");
            if (olderThanRelease(version)) {
                Span hint = new Span("обновится при следующем запуске");
                hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
                hint.setId("studioPresenceUpdate");
                add(hint);
            }
            var installer = StudioDownload.link(release, "Скачать установщик");
            installer.setId("downloadStudioInstaller");
            installer.getStyle().set("font-size", "var(--lumo-font-size-s)");
            add(installer);
            return;
        }
        status.setText("Студия не найдена на этом компьютере (или не запущена)");
        add(StudioDownload.link(release, "Установить студию"));
    }

    private boolean olderThanRelease(String studioVersion) {
        try {
            return StudioReleaseService.compareVersions(studioVersion, release.getVersionName()) < 0;
        } catch (PackageException ignored) {
            return false;
        }
    }
}
