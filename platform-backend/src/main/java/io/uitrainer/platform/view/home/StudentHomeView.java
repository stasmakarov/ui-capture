package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.lesson.StatusBadges;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

/** Пока нет портала: только опубликованные уроки, без черновиков, проверки и админки. */
@Route(value = "cabinet/student", layout = MainView.class)
@ViewController(id = PlatformViews.STUDENT_HOME)
public class StudentHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;

    public StudentHomeView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Опубликованные уроки";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Опубликованные уроки");
        title.getStyle().set("margin", "0");
        Span empty = new Span("Опубликованных уроков пока нет.");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Grid<Lesson> grid = new Grid<>(Lesson.class, false);
        grid.setId("publishedLessons");
        grid.setWidthFull();
        grid.addColumn(Lesson::getTitle).setHeader("Урок").setFlexGrow(3);
        grid.addColumn(lesson -> StatusBadges.date(lesson.getCreatedDate())).setHeader("Дата").setFlexGrow(1);
        grid.addItemClickListener(e -> open(e.getItem()));
        ListHolder holder = new ListHolder(dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.status = :status order by l.createdDate desc")
                .parameter("status", LessonStatus.PUBLISHED.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("publishedVersion", "_base"))
                .list());
        empty.setVisible(holder.lessons.isEmpty());
        grid.setItems(holder.lessons);
        getContent().add(title, empty, grid);
        getContent().expand(grid);
    }

    private void open(Lesson lesson) {
        if (lesson.getPublishedVersion() == null) {
            Notification.show("У этого урока ещё нет файла для просмотра", 4000, Notification.Position.MIDDLE);
            return;
        }
        getUI().ifPresent(ui -> ui.navigate("lessons/preview/" + lesson.getPublishedVersion().getId()));
    }

    private record ListHolder(java.util.List<Lesson> lessons) {
    }
}
