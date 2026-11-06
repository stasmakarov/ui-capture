package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.core.TimeSource;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.user.TeacherDialogs;
import io.uitrainer.platform.view.user.UserListView;
import org.springframework.beans.factory.annotation.Autowired;

/** Кабинет платформы: преподаватели, пользователи, роли и очередь проверки. Токены — в меню администрирования. */
@Route(value = "cabinet/admin", layout = MainView.class)
@ViewController(id = PlatformViews.ADMIN_HOME)
public class AdminHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private LessonLifecycleService lifecycle;
    @Autowired
    private TimeSource timeSource;
    @Autowired
    private UserProvisioning provisioning;
    @Autowired
    private ViewNavigators viewNavigators;

    private TeacherList teachers;

    public AdminHomeView() {
        getContent().setWidthFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Кабинет платформы";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Кабинет платформы");
        title.getStyle().set("margin", "0");
        Span lead = new Span("Преподаватели — это пользователи с ролью тренера. Отдельного справочника проверяющих нет.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button create = new Button("Создать преподавателя", e -> TeacherDialogs.createTeacher(provisioning, this::reloadTeachers));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setId("createTeacher");
        Button users = new Button("Пользователи", e -> viewNavigators.view(this, UserListView.class).navigate());
        users.setId("openUsers");
        Button roles = new Button("Роли", e -> viewNavigators.view(this, "sec_ResourceRoleModel.list").navigate());
        roles.setId("openRoles");
        Button rowRoles = new Button("Роли уровня строк", e -> viewNavigators.view(this, "sec_RowLevelRoleModel.list").navigate());
        rowRoles.setId("openRowRoles");
        HorizontalLayout links = new HorizontalLayout(create, users, roles, rowRoles);
        links.setWidthFull();
        links.getStyle().set("flex-wrap", "wrap");

        teachers = new TeacherList(dataManager, row -> getUI().ifPresent(ui -> ui.navigate(
                TeacherCoursesView.class, new RouteParameters("username", row.username()))));
        teachers.reload();

        H3 teachersTitle = new H3("Преподаватели");
        teachersTitle.getStyle().set("margin", "0");
        H3 queueTitle = new H3("Очередь проверки");
        queueTitle.getStyle().set("margin", "0");
        getContent().add(title, lead, links, teachersTitle, teachers, queueTitle,
                new ReviewQueuePanel(dataManager, lifecycle, timeSource));
    }

    private void reloadTeachers() {
        if (teachers != null) teachers.reload();
    }
}
