package io.uitrainer.platform.view.lesson;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.FluentLoader;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.studio.StudioLaunch;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/** Список уроков: фильтр по статусу, «Записать в студии». */
@Route(value = "lessons", layout = MainView.class)
@ViewController(id = PlatformViews.LESSON_LIST)
public class LessonListView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private StudioLaunch studioLaunch;
    @Autowired
    private CurrentUserAccess access;

    private final Grid<Lesson> grid = new Grid<>(Lesson.class, false);
    private final ComboBox<LessonStatus> statusFilter = new ComboBox<>("Статус");
    private final ComboBox<Course> courseField = new ComboBox<>("Курс для записи");

    public LessonListView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
    }

    @Override
    public String getPageTitle() {
        return "Уроки";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        statusFilter.setItems(LessonStatus.values());
        statusFilter.setItemLabelGenerator(StatusBadges::label);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("Все");
        statusFilter.setId("statusFilter");
        statusFilter.addValueChangeListener(e -> reload());

        HorizontalLayout toolbar = new HorizontalLayout(statusFilter);
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        if (access.isTrainer() || access.isAdmin()) {
            List<Course> courses = dataManager.load(Course.class).query("select c from uic_Course c order by c.title").list();
            courseField.setItems(courses);
            courseField.setItemLabelGenerator(Course::getTitle);
            courseField.setWidth("22em");
            courseField.setId("courseField");
            if (!courses.isEmpty()) courseField.setValue(courses.get(0));
            Button record = new Button("Записать в студии", VaadinIcon.CIRCLE.create(), e -> launchStudio(false));
            record.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            record.setId("recordInStudio");
            Button fallback = new Button("Студия уже открыта на этом компьютере", e -> launchStudio(true));
            fallback.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            fallback.setId("recordInStudioLocal");
            toolbar.add(courseField, record, fallback);
        }
        Button refresh = new Button("Обновить", VaadinIcon.REFRESH.create(), e -> reload());
        refresh.setId("refreshLessons");
        toolbar.add(refresh);

        grid.addColumn(Lesson::getTitle).setHeader("Урок").setFlexGrow(3).setSortable(true);
        grid.addColumn(Lesson::getExternalId).setHeader("id урока").setFlexGrow(2);
        grid.addColumn(l -> l.getAuthor() == null ? "" : l.getAuthor().getDisplayName()).setHeader("Автор").setFlexGrow(2);
        grid.addComponentColumn(l -> StatusBadges.badge(l.getStatus())).setHeader("Статус").setFlexGrow(1);
        grid.addColumn(l -> l.getCurrentVersion() == null ? "" : "v" + l.getCurrentVersion().getVersionNo()).setHeader("Версия").setFlexGrow(0).setWidth("6em");
        grid.addColumn(l -> l.getCourse() == null ? "" : l.getCourse().getTitle()).setHeader("Курс").setFlexGrow(2);
        grid.addColumn(l -> StatusBadges.date(l.getCreatedDate())).setHeader("Создан").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.setId("lessonsGrid");
        grid.addItemClickListener(e -> openLesson(e.getItem()));

        getContent().add(toolbar, grid);
        reload();
    }

    private void openLesson(Lesson lesson) {
        if (access.isStudent() && !access.isTrainer() && !access.isReviewer() && !access.isAdmin()) {
            if (lesson.getPublishedVersion() != null) {
                getUI().ifPresent(ui -> ui.navigate("lessons/preview/" + lesson.getPublishedVersion().getId()));
            }
            return;
        }
        getUI().ifPresent(ui -> ui.navigate("lessons/" + lesson.getId()));
    }

    private void reload() {
        LessonStatus status = statusFilter.getValue();
        String query = "select l from uic_Lesson l" + (status == null ? "" : " where l.status = :status") + " order by l.createdDate desc";
        FluentLoader.ByQuery<Lesson> loader = dataManager.load(Lesson.class).query(query)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("course", "_base")
                        .add("currentVersion", "_base").add("publishedVersion", "_base"));
        if (status != null) loader.parameter("status", status.getId());
        grid.setItems(loader.list());
    }

    private void launchStudio(boolean forceFallback) {
        Course course = courseField.getValue();
        studioLaunch.launch(this, forceFallback, course == null ? null : course.getId());
    }
}
