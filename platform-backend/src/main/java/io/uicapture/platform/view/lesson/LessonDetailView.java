package io.uicapture.platform.view.lesson;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uicapture.platform.entity.*;
import io.uicapture.platform.security.PlatformViews;
import io.uicapture.platform.service.AccessException;
import io.uicapture.platform.service.LessonLifecycleService;
import io.uicapture.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

/** Карточка урока: версии, журнал переходов, кнопки переходов ЖЦ, предпросмотр. */
@Route(value = "lessons/:id", layout = MainView.class)
@ViewController(id = PlatformViews.LESSON_DETAIL)
public class LessonDetailView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private LessonLifecycleService lifecycle;

    private UUID lessonId;
    private final H2 title = new H2();
    private final HorizontalLayout info = new HorizontalLayout();
    private final HorizontalLayout actions = new HorizontalLayout();
    private final TextArea comment = new TextArea("Комментарий к переходу");
    private final Grid<LessonVersion> versions = new Grid<>(LessonVersion.class, false);
    private final Grid<LifecycleTransition> journal = new Grid<>(LifecycleTransition.class, false);
    private final Grid<StepRef> steps = new Grid<>(StepRef.class, false);

    public LessonDetailView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        title.getStyle().set("margin", "0");
        info.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        comment.setWidth("40em");
        comment.setPlaceholder("Обязателен при возврате на доработку");
        comment.setId("transitionComment");
        actions.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        versions.addColumn(v -> "v" + v.getVersionNo()).setHeader("Версия").setWidth("6em").setFlexGrow(0);
        versions.addColumn(LessonVersion::getTitle).setHeader("Заголовок").setFlexGrow(3);
        versions.addColumn(LessonVersion::getStepCount).setHeader("Шагов").setWidth("7em").setFlexGrow(0);
        versions.addColumn(LessonVersion::getUploadedBy).setHeader("Загрузил").setFlexGrow(1);
        versions.addColumn(v -> StatusBadges.date(v.getCreatedDate())).setHeader("Загружена").setFlexGrow(1);
        versions.addColumn(v -> v.getContentSha256() == null ? "" : v.getContentSha256().substring(0, 12) + "…").setHeader("SHA-256 lesson.json").setFlexGrow(1);
        versions.addComponentColumn(v -> {
            Button preview = new Button("Предпросмотр", VaadinIcon.EYE.create(),
                    e -> getUI().ifPresent(ui -> ui.navigate("lessons/preview/" + v.getId())));
            preview.addThemeVariants(ButtonVariant.LUMO_SMALL);
            preview.setId("preview-v" + v.getVersionNo());
            return preview;
        }).setHeader("").setFlexGrow(1);
        versions.setId("versionsGrid");

        journal.addColumn(t -> StatusBadges.date(t.getAt())).setHeader("Когда").setFlexGrow(1);
        journal.addColumn(LifecycleTransition::getActor).setHeader("Кто").setFlexGrow(1);
        journal.addColumn(t -> t.getFromStatus() == null ? "—" : StatusBadges.label(t.getFromStatus())).setHeader("Из статуса").setFlexGrow(1);
        journal.addColumn(t -> StatusBadges.label(t.getToStatus())).setHeader("В статус").setFlexGrow(1);
        journal.addColumn(t -> t.getLessonVersion() == null ? "" : "v" + t.getLessonVersion().getVersionNo()).setHeader("Версия").setWidth("6em").setFlexGrow(0);
        journal.addColumn(LifecycleTransition::getComment).setHeader("Комментарий").setFlexGrow(3);
        journal.setId("journalGrid");

        steps.addColumn(s -> s.getIndex() + 1).setHeader("№").setWidth("5em").setFlexGrow(0);
        steps.addColumn(StepRef::getAction).setHeader("Действие").setWidth("8em").setFlexGrow(0);
        steps.addColumn(StepRef::getInstructionShort).setHeader("Инструкция").setFlexGrow(4);
        steps.addColumn(StepRef::getStepId).setHeader("id шага").setFlexGrow(1);

        TabSheet tabs = new TabSheet();
        tabs.add("Версии", versions);
        tabs.add("Журнал переходов", journal);
        tabs.add("Шаги текущей версии", steps);
        tabs.setSizeFull();

        getContent().add(title, info, new H4("Жизненный цикл"), actions, tabs);
    }

    @Override
    public String getPageTitle() {
        return "Карточка урока";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        lessonId = event.getRouteParameters().get("id").map(UUID::fromString).orElse(null);
        super.beforeEnter(event);
        refresh();
    }

    private void refresh() {
        Lesson lesson = dataManager.load(Lesson.class).id(lessonId)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("course", "_base")
                        .add("currentVersion", "_base").add("publishedVersion", "_base"))
                .optional().orElse(null);
        if (lesson == null) {
            title.setText("Урок не найден или нет доступа");
            return;
        }
        title.setText(lesson.getTitle());
        info.removeAll();
        info.add(StatusBadges.badge(lesson.getStatus()),
                new Span("id: " + lesson.getExternalId()),
                new Span("автор: " + (lesson.getAuthor() == null ? "" : lesson.getAuthor().getDisplayName())),
                new Span("курс: " + (lesson.getCourse() == null ? "—" : lesson.getCourse().getTitle())),
                new Span("опубликована: " + (lesson.getPublishedVersion() == null ? "—" : "v" + lesson.getPublishedVersion().getVersionNo())));
        info.getChildren().skip(1).forEach(c -> c.getElement().getStyle().set("color", "var(--lumo-secondary-text-color)"));

        actions.removeAll();
        List<LessonStatus> available = lifecycle.availableTransitions(lesson);
        if (available.isEmpty()) {
            actions.add(new Span("Доступных переходов нет"));
        } else {
            actions.add(comment);
            for (LessonStatus to : available) {
                Button button = new Button(StatusBadges.action(lesson.getStatus(), to), e -> doTransition(to));
                button.setId("transition-" + to.getId().toLowerCase());
                if (to == LessonStatus.PUBLISHED || to == LessonStatus.REVIEW) button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                if (to == LessonStatus.DRAFT || to == LessonStatus.ARCHIVED) button.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
                actions.add(button);
            }
        }

        versions.setItems(dataManager.load(LessonVersion.class)
                .query("select v from uic_LessonVersion v where v.lesson.id = :l order by v.versionNo desc")
                .parameter("l", lessonId).fetchPlan("_base").list());
        journal.setItems(dataManager.load(LifecycleTransition.class)
                .query("select t from uic_LifecycleTransition t where t.lesson.id = :l order by t.at desc")
                .parameter("l", lessonId)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("lessonVersion", "_base")).list());
        if (lesson.getCurrentVersion() != null) {
            steps.setItems(dataManager.load(StepRef.class)
                    .query("select s from uic_StepRef s where s.lessonVersion.id = :v order by s.index")
                    .parameter("v", lesson.getCurrentVersion().getId()).list());
        }
    }

    private void doTransition(LessonStatus to) {
        try {
            Lesson lesson = lifecycle.transition(lessonId, to, comment.getValue());
            comment.clear();
            Notification.show("Статус: " + StatusBadges.label(lesson.getStatus()), 3000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refresh();
        } catch (AccessException | IllegalArgumentException | IllegalStateException e) {
            Notification.show(e.getMessage(), 5000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
