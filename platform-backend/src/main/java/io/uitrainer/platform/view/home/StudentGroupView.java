package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.CourseProgressStatus;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@Route(value = "cabinet/student/groups/:groupId", layout = MainView.class)
@ViewController(id = PlatformViews.STUDENT_GROUP)
public class StudentGroupView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private GroupProgressService groupProgress;

    private final H2 title = new H2();
    private final Span empty = new Span();
    private final Grid<GroupCourse> grid = new Grid<>(GroupCourse.class, false);
    private GroupProgressService.GroupSnapshot snapshot;

    public StudentGroupView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
        title.getStyle().set("margin", "0");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        grid.setId("studentCourses");
        grid.setWidthFull();
        grid.addColumn(gc -> gc.getCourse().getTitle()).setHeader("Курс").setFlexGrow(2);
        grid.addColumn(this::progressLabel).setHeader("Прогресс").setFlexGrow(1);
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(StudentCourseView.class, new RouteParameters(
                new RouteParam("groupId", e.getItem().getGroup().getId().toString()),
                new RouteParam("courseId", e.getItem().getCourse().getId().toString())))));
        Button back = new Button("К группам", e -> getUI().ifPresent(ui -> ui.navigate(StudentHomeView.class)));
        back.setId("backToStudentHome");
        getContent().add(title, back, empty, grid);
        getContent().expand(grid);
    }

    @Override
    public String getPageTitle() {
        return title.getText().isBlank() ? "Группа" : title.getText();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        super.beforeEnter(event);
        UUID groupId = parseUuid(event.getRouteParameters().get("groupId").orElse(""));
        if (groupId == null) {
            return;
        }
        StudyGroup group;
        try {
            group = groupProgress.loadGroup(groupId);
        } catch (RuntimeException ex) {
            title.setText("Группа не найдена");
            empty.setText("Нет доступа к этой группе.");
            empty.setVisible(true);
            grid.setVisible(false);
            return;
        }
        title.setText(group.getName());
        User student = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .one();
        groupProgress.recalculateStudentInGroup(group, student);
        snapshot = groupProgress.snapshot(group, student);
        List<GroupCourse> courses = snapshot.accessible();
        empty.setText("Нет открытых курсов.");
        empty.setVisible(courses.isEmpty());
        grid.setVisible(true);
        grid.setItems(courses);
    }

    private String progressLabel(GroupCourse gc) {
        CourseProgressStatus status = snapshot == null
                ? CourseProgressStatus.NOT_STARTED
                : snapshot.status(gc.getCourse().getId());
        return switch (status) {
            case NOT_STARTED -> "Не начат";
            case STARTED -> "Начат";
            case COMPLETED -> "Пройден";
        };
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            Notification.show("Неверная ссылка", 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
            getUI().ifPresent(ui -> ui.navigate(StudentHomeView.class));
            return null;
        }
    }
}
