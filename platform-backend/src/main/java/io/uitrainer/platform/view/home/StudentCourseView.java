package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParam;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonAttempt;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.service.LessonProgressService;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Route(value = "cabinet/student/groups/:groupId/courses/:courseId", layout = MainView.class)
@ViewController(id = PlatformViews.STUDENT_COURSE)
public class StudentCourseView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private GroupProgressService groupProgress;
    @Autowired
    private LessonProgressService lessonProgress;

    private final H2 title = new H2();
    private final Span empty = new Span();
    private final Grid<Lesson> grid = new Grid<>(Lesson.class, false);
    private UUID groupId;
    private UUID courseId;
    private User student;
    private Map<UUID, LessonAttempt> attempts = Map.of();

    public StudentCourseView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
        title.getStyle().set("margin", "0");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        grid.setId("studentLessons");
        grid.setWidthFull();
        grid.addColumn(this::lessonLabel).setHeader("Урок").setFlexGrow(2);
        grid.addColumn(this::attemptLabel).setHeader("Статус").setFlexGrow(1);
        grid.addComponentColumn(this::actions).setHeader("").setFlexGrow(2);
        getContent().add(title, empty, grid);
        getContent().expand(grid);
    }

    @Override
    public String getPageTitle() {
        return title.getText().isBlank() ? "Курс" : title.getText();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        super.beforeEnter(event);
        groupId = parseUuid(event.getRouteParameters().get("groupId").orElse(""));
        courseId = parseUuid(event.getRouteParameters().get("courseId").orElse(""));
        if (groupId == null || courseId == null) {
            return;
        }
        Button back = new Button("К группе", e -> getUI().ifPresent(ui -> ui.navigate(
                StudentGroupView.class, new RouteParameters("groupId", groupId.toString()))));
        back.setId("backToStudentGroup");
        if (getContent().getComponentCount() == 3) {
            getContent().addComponentAtIndex(1, back);
        }
        student = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .one();
        StudyGroup group;
        Course course;
        try {
            group = groupProgress.loadGroup(groupId);
            course = dataManager.load(Course.class).id(courseId).fetchPlan(FetchPlan.BASE).one();
        } catch (RuntimeException ex) {
            title.setText("Курс не найден");
            empty.setText("Нет доступа к этому курсу.");
            empty.setVisible(true);
            grid.setVisible(false);
            return;
        }
        groupProgress.recalculateStudentInGroup(group, student);
        if (!groupProgress.isCourseAccessible(group, student, course)) {
            title.setText("Курс закрыт");
            empty.setText("Этот курс пока недоступен.");
            empty.setVisible(true);
            grid.setVisible(false);
            return;
        }
        title.setText(course.getTitle());
        List<Lesson> lessons = lessonProgress.publishedLessons(course);
        attempts = new HashMap<>();
        for (LessonAttempt attempt : lessonProgress.attemptsFor(student, lessons)) {
            attempts.put(attempt.getLesson().getId(), attempt);
        }
        empty.setText("Опубликованных уроков нет.");
        empty.setVisible(lessons.isEmpty());
        grid.setVisible(true);
        grid.setItems(lessons);
    }

    private HorizontalLayout actions(Lesson lesson) {
        LessonAttempt attempt = attempts.get(lesson.getId());
        Button start = new Button("Начать урок", e -> run(() -> {
            LessonAttempt started = lessonProgress.startLesson(lesson.getId());
            attempts.put(lesson.getId(), started);
            openPreview(lesson);
            reload();
        }));
        start.setId("start-" + lesson.getId());
        Button complete = new Button("Отметить пройденным", e -> run(() -> {
            lessonProgress.completeLesson(lesson.getId());
            reload();
        }));
        complete.setId("complete-" + lesson.getId());
        complete.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button preview = new Button("Открыть", e -> openPreview(lesson));
        preview.setId("preview-" + lesson.getId());
        if (attempt == null) {
            complete.setEnabled(false);
        }
        if (attempt != null && attempt.getCompletedAt() != null) {
            start.setVisible(false);
            complete.setVisible(false);
        }
        return new HorizontalLayout(start, complete, preview);
    }

    private String lessonLabel(Lesson lesson) {
        Integer order = lesson.getSortOrder();
        String prefix = order == null ? "Урок" : "Урок " + order + ".";
        return prefix + " " + lesson.getTitle();
    }

    private String attemptLabel(Lesson lesson) {
        LessonAttempt attempt = attempts.get(lesson.getId());
        if (attempt == null) {
            return "Не начат";
        }
        return attempt.getCompletedAt() == null ? "Начат" : "Пройден";
    }

    private void openPreview(Lesson lesson) {
        LessonAttempt attempt = attempts.get(lesson.getId());
        UUID versionId = attempt != null && attempt.getLessonVersion() != null
                ? attempt.getLessonVersion().getId()
                : lesson.getPublishedVersion() == null ? null : lesson.getPublishedVersion().getId();
        if (versionId == null) {
            Notification.show("У этого урока ещё нет файла для просмотра", 4000, Notification.Position.MIDDLE);
            return;
        }
        getUI().ifPresent(ui -> ui.navigate("lessons/preview/" + versionId));
    }

    private void run(Runnable action) {
        try {
            action.run();
        } catch (ConflictException ex) {
            Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void reload() {
        getUI().ifPresent(ui -> ui.navigate(StudentCourseView.class, new RouteParameters(
                new RouteParam("groupId", groupId.toString()),
                new RouteParam("courseId", courseId.toString()))));
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
