package io.uitrainer.platform.view.studio;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinServletRequest;
import io.uitrainer.platform.service.StudioTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Кнопка «Записать в студии»: одноразовый тикет, ссылка {@code uitrainer://}
 * (алиас {@code uicapture://} студия тоже принимает) и запасной пинг локальной студии
 * на {@code http://127.0.0.1:44731}. Тикет привязан к курсу ({@code X-Course-Id} после обмена).
 */
@org.springframework.stereotype.Component
public class StudioLaunch {

    /**
     * Сначала схема uitrainer://, затем (если окно не потеряло фокус) пинг локальной студии
     * и передача ей того же задания через её API. Возвращает scheme | local | none.
     */
    static final String LAUNCH_JS = """
            const link = $0, server = $1, ticket = $2, course = $3, studio = $4, forceFallback = $5;
            return new Promise((resolve) => {
              let left = false;
              const onBlur = () => { left = true; };
              window.addEventListener('blur', onBlur);
              let frame = null;
              if (!forceFallback) {
                frame = document.createElement('iframe');
                frame.style.display = 'none';
                frame.src = link;
                document.body.appendChild(frame);
              }
              setTimeout(async () => {
                window.removeEventListener('blur', onBlur);
                if (frame) frame.remove();
                if (left && !forceFallback) { resolve('scheme'); return; }
                try {
                  await fetch(studio + '/api/health', { mode: 'no-cors', cache: 'no-store' });
                  await fetch(studio + '/api/platform/link', {
                    method: 'POST', mode: 'no-cors', headers: { 'Content-Type': 'text/plain' },
                    body: JSON.stringify({ server, ticket, course })
                  });
                  window.open(studio + '/?linked=1', '_blank');
                  resolve('local');
                } catch (e) {
                  resolve('none');
                }
              }, forceFallback ? 50 : 1500);
            });
            """;

    private final StudioTokenService tokenService;
    private final String publicUrl;
    private final String studioUrl;

    public StudioLaunch(StudioTokenService tokenService,
                        @Value("${uitrainer.public-url:}") String publicUrl,
                        @Value("${uitrainer.studio.local-url:http://127.0.0.1:44731}") String studioUrl) {
        this.tokenService = tokenService;
        this.publicUrl = publicUrl;
        this.studioUrl = studioUrl;
    }

    public static String studioLink(String server, String ticket, String courseId) {
        return "uitrainer://new?course=" + enc(courseId) + "&server=" + enc(server) + "&ticket=" + enc(ticket);
    }

    public void launch(com.vaadin.flow.component.Component source, boolean forceFallback, @Nullable UUID courseId) {
        String ticket = tokenService.issueTicket(courseId);
        String server = serverUrl();
        String course = courseId == null ? "" : courseId.toString();
        String link = studioLink(server, ticket, course);
        source.getUI().ifPresent(ui -> ui.getPage().executeJs(LAUNCH_JS, link, server, ticket, course, studioUrl, forceFallback)
                .then(String.class, result -> onLaunchResult(result, link)));
    }

    private void onLaunchResult(String result, String link) {
        if ("scheme".equals(result)) {
            Notification.show("Студия открыта по ссылке uitrainer://. Запишите урок и нажмите «Опубликовать на сервер».", 6000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else if ("local".equals(result)) {
            Notification.show("Локальная студия (127.0.0.1:44731) получила задание и открыта в новой вкладке.", 6000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Студия не найдена");
            TextField linkField = new TextField("Ссылка для студии (действует 5 минут)");
            linkField.setValue(link);
            linkField.setReadOnly(true);
            linkField.setWidthFull();
            dialog.add(new Paragraph("Не удалось открыть uitrainer:// и не отвечает локальная студия на 127.0.0.1:44731."),
                    new Html("<ol><li>Установите студию: <code>git clone</code> репозитория ui-capture, <code>npm install</code>.</li>"
                            + "<li>Запустите: <code>npm start</code> (откроется http://127.0.0.1:44731).</li>"
                            + "<li>Нажмите «Записать в студии» ещё раз — или выполните <code>npx uitrainer open-url \"…\"</code> со ссылкой ниже. Старая команда <code>npx ui-capture</code> тоже работает.</li></ol>"),
                    linkField);
            dialog.setWidth("40em");
            dialog.getFooter().add(new Button("Закрыть", e -> dialog.close()));
            dialog.open();
        }
    }

    private String serverUrl() {
        if (publicUrl != null && !publicUrl.isBlank()) return publicUrl.replaceAll("/+$", "");
        HttpServletRequest request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        int port = request.getServerPort();
        boolean defaultPort = (port == 80 && "http".equals(request.getScheme())) || (port == 443 && "https".equals(request.getScheme()));
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
