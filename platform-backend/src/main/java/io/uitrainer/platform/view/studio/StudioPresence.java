package io.uitrainer.platform.view.studio;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;
import org.springframework.lang.Nullable;

/**
 * Блок кабинета: спрашивает локальную студию {@code GET /api/presence} из браузера.
 */
public class StudioPresence extends VerticalLayout {

    private static final String PROBE_JS = """
            const p = (navigator.userAgentData && navigator.userAgentData.platform) || navigator.platform || '';
            const ua = navigator.userAgent || '';
            const win = /Win/i.test(p) || /Windows/i.test(ua);
            if (!win) return 'os:other';
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

    @Nullable
    private final StudioRelease release;
    private final Span status = new Span();

    public StudioPresence(@Nullable StudioRelease release, String localUrl) {
        this.release = release;
        setPadding(false);
        setSpacing(true);
        status.setId("studioPresenceStatus");
        add(status);
        if (release == null) {
            status.setText("Устанавливать пока нечего");
            return;
        }
        status.setText("Проверяем студию…");
        addAttachListener(event -> event.getUI().getPage().executeJs(PROBE_JS, localUrl)
                .then(String.class, this::applyResult));
    }

    private void applyResult(String result) {
        removeAll();
        add(status);
        if ("os:other".equals(result)) {
            status.setText("Студия для Windows, на этом компьютере установить нельзя");
            return;
        }
        if (result != null && result.startsWith("ok:")) {
            String version = result.substring(3);
            status.setText("Установлена, версия " + version);
            if (release != null && olderThanRelease(version)) {
                Span hint = new Span("(обновится до " + release.getVersionName() + " при следующем запуске)");
                hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
                hint.setId("studioPresenceUpdate");
                add(hint);
            }
            return;
        }
        status.setText("Не найдена на этом компьютере (или не запущена)");
        if (release != null) {
            add(StudioDownload.link(release, "Установить"));
        }
    }

    private boolean olderThanRelease(String studioVersion) {
        try {
            return StudioReleaseService.compareVersions(studioVersion, release.getVersionName()) < 0;
        } catch (PackageException ignored) {
            return false;
        }
    }
}
