package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.view.group.StudyGroupListView;
import io.uitrainer.platform.service.CourseAccessService;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.service.StudioReleaseService;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.studio.StudioDownload;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Кабинет преподавателя: активные группы, курсы и сводка уроков. */
@Route(value = "cabinet/trainer", layout = MainView.class)
@ViewController(id = PlatformViews.TRAINER_HOME)
public class TrainerHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private GroupProgressService groupProgress;
    @Autowired
    private CourseAccessService courseAccess;
    @Autowired
    private StudioReleaseService releases;

    private final Grid<GroupProgressService.GroupSummary> groups = new Grid<>();
    private final Checkbox history = new Checkbox("История закрытых");

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
        Span lead = new Span("Активные группы и курсы. Закрытые группы — в истории.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button createGroup = new Button("Создать группу", e -> viewNavigators.detailView(this, StudyGroup.class)
                .newEntity()
                .withBackwardNavigation(true)
                .navigate());
        createGroup.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        createGroup.setId("createGroup");
        Button createCourse = new Button("Создать курс", e -> viewNavigators.detailView(this, Course.class)
                .newEntity()
                .withBackwardNavigation(true)
                .navigate());
        createCourse.setId("createCourse");
        HorizontalLayout actions = new HorizontalLayout(createGroup, createCourse);
        actions.getStyle().set("flex-wrap", "wrap");

        HorizontalLayout counters = new HorizontalLayout();
        counters.setWidthFull();
        counters.getStyle().set("flex-wrap", "wrap");
        counters.add(counter(LessonBuckets.DRAFT));
        counters.add(counter(LessonBuckets.PUBLISHED));
        if (LessonBuckets.count(dataManager, LessonBuckets.REVIEW) > 0) counters.add(counter(LessonBuckets.REVIEW));
        if (LessonBuckets.count(dataManager, LessonBuckets.RETURNED) > 0) counters.add(counter(LessonBuckets.RETURNED));

        groups.setId("trainerGroups");
        groups.setWidthFull();
        groups.setMinHeight("12em");
        groups.addColumn(row -> row.group().getName()).setHeader("Группа").setFlexGrow(2);
        groups.addColumn(row -> row.group().getStatus() == null ? "" : statusLabel(row.group().getStatus())).setHeader("Статус").setFlexGrow(1);
        groups.addColumn(GroupProgressService.GroupSummary::members).setHeader("Ученики").setWidth("8em").setFlexGrow(0);
        groups.addColumn(GroupProgressService.GroupSummary::progressLabel).setHeader("Прогресс").setFlexGrow(1);
        groups.addItemClickListener(e -> viewNavigators.detailView(this, StudyGroup.class)
                .editEntity(e.getItem().group())
                .withBackwardNavigation(true)
                .navigate());
        history.setId("showClosedGroups");
        history.addValueChangeListener(e -> reloadGroups());
        reloadGroups();

        Grid<Course> courses = new Grid<>(Course.class, false);
        courses.setId("trainerCourses");
        courses.setWidthFull();
        courses.setMinHeight("12em");
        List<Course> courseItems = dataManager.load(Course.class)
                .query("select c from uic_Course c order by c.title")
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("owner", FetchPlan.INSTANCE_NAME))
                .list();
        Map<UUID, String> roles = courseAccess.roleLabels(courseItems);
        courses.addColumn(Course::getTitle).setHeader("Курс").setFlexGrow(2);
        courses.addColumn(Course::getCode).setHeader("Код").setFlexGrow(1);
        courses.addColumn(c -> roles.getOrDefault(c.getId(), "")).setHeader("Роль").setFlexGrow(1);
        courses.addItemClickListener(e -> viewNavigators.detailView(this, Course.class)
                .editEntity(e.getItem())
                .withBackwardNavigation(true)
                .navigate());
        courses.setItems(courseItems);

        H3 groupsTitle = new H3("Мои группы");
        groupsTitle.getStyle().set("margin", "0");
        Button allGroups = new Button("Все группы", e -> viewNavigators.view(this, StudyGroupListView.class).navigate());
        allGroups.setId("allGroups");
        HorizontalLayout groupsHeader = new HorizontalLayout(groupsTitle, allGroups);
        groupsHeader.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
        H3 coursesTitle = new H3("Курсы");
        coursesTitle.getStyle().set("margin", "0");
        getContent().add(title, lead, actions, counters, groupsHeader, history, groups, coursesTitle, courses, studioBlock());
        getContent().expand(courses);
    }

    private void reloadGroups() {
        boolean includeClosed = Boolean.TRUE.equals(history.getValue());
        List<StudyGroup> list;
        if (includeClosed) {
            list = dataManager.load(StudyGroup.class)
                    .query("select g from uic_StudyGroup g order by g.name")
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                    .list();
        } else {
            list = dataManager.load(StudyGroup.class)
                    .query("select g from uic_StudyGroup g where g.status <> :closed order by g.name")
                    .parameter("closed", GroupStatus.CLOSED.getId())
                    .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                    .list();
        }
        groups.setItems(groupProgress.summarize(list).values());
    }

    private static String statusLabel(GroupStatus status) {
        return switch (status) {
            case CREATED -> "Создана";
            case ACTIVE -> "Активна";
            case CLOSED -> "Закрыта";
        };
    }

    private VerticalLayout studioBlock() {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(false);
        box.setSpacing(true);
        H3 heading = new H3("Студия");
        heading.getStyle().set("margin", "0");
        box.add(heading);
        var current = releases.current(StudioReleaseService.WIN);
        if (current.isEmpty()) {
            box.add(new Span("Студия ещё не загружена, обратитесь к администратору"));
        } else {
            StudioRelease release = current.get();
            box.add(new Span("Версия " + release.getVersionName()));
            box.add(StudioDownload.link(release));
        }
        return box;
    }

    private Button counter(String bucket) {
        long count = LessonBuckets.count(dataManager, bucket);
        Button button = new Button(LessonBuckets.title(bucket) + ": " + count, e -> getUI().ifPresent(ui ->
                ui.navigate(LessonWorkQueueView.class, new RouteParameters("bucket", bucket))));
        button.setId("counter-" + bucket);
        return button;
    }
}
