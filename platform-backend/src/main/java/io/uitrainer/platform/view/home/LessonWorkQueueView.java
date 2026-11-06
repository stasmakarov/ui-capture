package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.view.lesson.StatusBadges;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

/** Очередь уроков по счётчику кабинета. В меню преподавателя её нет. */
@Route(value = "cabinet/work/:bucket", layout = MainView.class)
@ViewController(id = PlatformViews.LESSON_QUEUE)
public class LessonWorkQueueView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentUserAccess access;

    private String bucket = "";
    private final H2 title = new H2();
    private final Span empty = new Span("В этой очереди уроков нет.");
    private final Grid<Lesson> grid = new Grid<>(Lesson.class, false);

    public LessonWorkQueueView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
        title.getStyle().set("margin", "0");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        grid.setId("lessonQueue");
        grid.setWidthFull();
        grid.addColumn(Lesson::getTitle).setHeader("Урок").setFlexGrow(2);
        grid.addColumn(lesson -> lesson.getCourse() == null ? "—" : lesson.getCourse().getTitle()).setHeader("Курс").setFlexGrow(2);
        grid.addComponentColumn(lesson -> StatusBadges.badge(lesson.getStatus())).setHeader("Статус").setFlexGrow(1);
        grid.addColumn(lesson -> StatusBadges.date(lesson.getCreatedDate())).setHeader("Создан").setFlexGrow(1);
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate("lessons/" + e.getItem().getId())));
        Button back = new Button("К кабинету", e -> getUI().ifPresent(ui -> HomeViews.openLanding(ui, access)));
        back.setId("backToCabinet");
        getContent().add(title, back, empty, grid);
        getContent().expand(grid);
    }

    @Override
    public String getPageTitle() {
        return LessonBuckets.title(bucket);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        bucket = event.getRouteParameters().get("bucket").orElse("");
        super.beforeEnter(event);
        title.setText(LessonBuckets.title(bucket));
        if (!LessonBuckets.known(bucket)) {
            grid.setVisible(false);
            empty.setText("Неизвестная очередь");
            empty.setVisible(true);
            return;
        }
        var lessons = LessonBuckets.list(dataManager, bucket);
        empty.setVisible(lessons.isEmpty());
        empty.setText("В этой очереди уроков нет.");
        grid.setVisible(true);
        grid.setItems(lessons);
    }
}
