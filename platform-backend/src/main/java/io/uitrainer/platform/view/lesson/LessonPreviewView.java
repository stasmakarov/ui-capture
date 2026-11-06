package io.uitrainer.platform.view.lesson;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.IFrame;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import io.jmix.core.DataManager;
import io.jmix.core.FileStorageLocator;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

/** Предпросмотр версии: trainer.html из пакета в изолированном iframe. */
@Route(value = "lessons/preview/:versionId", layout = MainView.class)
@ViewController(id = PlatformViews.LESSON_PREVIEW)
public class LessonPreviewView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private FileStorageLocator fileStorageLocator;

    public LessonPreviewView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
    }

    @Override
    public String getPageTitle() {
        return "Предпросмотр урока";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        UUID versionId = event.getRouteParameters().get("versionId").map(UUID::fromString).orElse(null);
        super.beforeEnter(event);
        getContent().removeAll();
        LessonVersion version = versionId == null ? null : dataManager.load(LessonVersion.class).id(versionId)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("lesson", "_base")).optional().orElse(null);
        if (version == null) {
            getContent().add(new Paragraph("Версия не найдена или нет доступа."));
            return;
        }
        Button back = new Button("К уроку", VaadinIcon.ARROW_LEFT.create(),
                e -> getUI().ifPresent(ui -> ui.navigate("lessons/" + version.getLesson().getId())));
        HorizontalLayout header = new HorizontalLayout(back, new Span(version.getLesson().getTitle() + " · v" + version.getVersionNo()));
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        getContent().add(header);
        if (version.getTrainerHtml() == null) {
            getContent().add(new Paragraph("В пакете этой версии нет trainer.html. Опубликуйте урок из студии ещё раз — "
                    + "кнопка «Опубликовать на сервер» кладёт trainer.html в пакет."));
            return;
        }
        StreamResource resource = new StreamResource("trainer.html",
                () -> fileStorageLocator.getDefault().openStream(version.getTrainerHtml()));
        resource.setContentType("text/html; charset=utf-8");
        IFrame frame = new IFrame();
        frame.getElement().setAttribute("src", resource);
        // trainer.html содержит снимки чужих сайтов: без allow-same-origin он не видит сессию админки
        frame.setSandbox(IFrame.SandboxType.ALLOW_SCRIPTS, IFrame.SandboxType.ALLOW_POPUPS, IFrame.SandboxType.ALLOW_FORMS, IFrame.SandboxType.ALLOW_MODALS);
        frame.setSizeFull();
        frame.addClassName("uic-preview-frame");
        frame.setId("trainerPreview");
        getContent().add(frame);
        getContent().setFlexGrow(1, frame);
    }
}
