package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

/** Кабинет преподавателя: свои курсы и сводка уроков. Общего списка уроков и токенов нет. */
@Route(value = "cabinet/trainer", layout = MainView.class)
@ViewController(id = PlatformViews.TRAINER_HOME)
public class TrainerHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;

    public TrainerHomeView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Кабинет преподавателя";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Кабинет преподавателя");
        title.getStyle().set("margin", "0");
        Span lead = new Span("Ваши курсы. Счётчик открывает очередь уроков.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button create = new Button("Создать курс", e -> viewNavigators.detailView(this, Course.class)
                .newEntity()
                .withBackwardNavigation(true)
                .navigate());
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setId("createCourse");

        HorizontalLayout counters = new HorizontalLayout();
        counters.setWidthFull();
        counters.getStyle().set("flex-wrap", "wrap");
        counters.add(counter(LessonBuckets.DRAFT));
        counters.add(counter(LessonBuckets.PUBLISHED));
        if (LessonBuckets.count(dataManager, LessonBuckets.REVIEW) > 0) counters.add(counter(LessonBuckets.REVIEW));
        if (LessonBuckets.count(dataManager, LessonBuckets.RETURNED) > 0) counters.add(counter(LessonBuckets.RETURNED));

        Grid<Course> courses = new Grid<>(Course.class, false);
        courses.setId("trainerCourses");
        courses.setWidthFull();
        courses.setMinHeight("16em");
        courses.addColumn(Course::getTitle).setHeader("Курс").setFlexGrow(2);
        courses.addColumn(Course::getCode).setHeader("Код").setFlexGrow(1);
        courses.addItemClickListener(e -> viewNavigators.detailView(this, Course.class)
                .editEntity(e.getItem())
                .withBackwardNavigation(true)
                .navigate());
        courses.setItems(dataManager.load(Course.class)
                .query("select c from uic_Course c order by c.title")
                .fetchPlan("_base")
                .list());

        H3 coursesTitle = new H3("Курсы");
        coursesTitle.getStyle().set("margin", "0");
        getContent().add(title, lead, create, counters, coursesTitle, courses);
        getContent().expand(courses);
    }

    private Button counter(String bucket) {
        long count = LessonBuckets.count(dataManager, bucket);
        Button button = new Button(LessonBuckets.title(bucket) + ": " + count, e -> getUI().ifPresent(ui ->
                ui.navigate(LessonWorkQueueView.class, new RouteParameters("bucket", bucket))));
        button.setId("counter-" + bucket);
        return button;
    }
}
