package io.uitrainer.platform.view.about;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.server.VaadinSession;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.AppVersionService;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

@DialogMode(width = "28rem")
@ViewController(id = PlatformViews.ABOUT)
@ViewDescriptor(path = "about-view.xml")
public class AboutView extends StandardView {

    @Autowired
    private AppVersionService appVersionService;
    @ViewComponent
    private MessageBundle messageBundle;
    @ViewComponent
    private Span platformLabel;
    @ViewComponent
    private Span buildLabel;
    @ViewComponent
    private Span dateLabel;
    @ViewComponent
    private Span jmixLabel;
    @ViewComponent
    private Span apiLabel;
    @ViewComponent
    private Span studioLabel;

    @Subscribe
    public void onBeforeShow(final BeforeShowEvent event) {
        AppVersionService.Info info = appVersionService.current();
        platformLabel.setText(line("platform.line", info.version()));
        buildLabel.setText(String.format(messageBundle.getMessage("build.line"),
                display(info.commit()), display(info.branch())));
        dateLabel.setText(line("date.line", formatDate(info)));
        jmixLabel.setText(line("jmix.line", info.jmixVersion()));
        apiLabel.setText(line("api.line", String.valueOf(info.apiVersion())));
        String studio = info.studioVersion() == null
                ? messageBundle.getMessage("studio.missing")
                : info.studioVersion();
        studioLabel.setText(line("studio.line", studio));
    }

    private String line(String key, String value) {
        return String.format(messageBundle.getMessage(key), display(value));
    }

    private String display(String value) {
        return value != null ? value : messageBundle.getMessage("unknown");
    }

    private String formatDate(AppVersionService.Info info) {
        if (info.date() == null) {
            return null;
        }
        Locale locale = VaadinSession.getCurrent() != null
                ? VaadinSession.getCurrent().getLocale()
                : Locale.getDefault();
        return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                .withLocale(locale)
                .withZone(ZoneId.systemDefault())
                .format(info.date());
    }
}
