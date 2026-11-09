package io.uitrainer.platform.view.user;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.security.UserManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Пользователи платформы и назначение ролей. Проверяющий здесь — роль, не отдельная сущность. */
@Route(value = "users", layout = MainView.class)
@ViewController(id = PlatformViews.USER_LIST)
public class UserListView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private UserProvisioning provisioning;
    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private UserManager userManager;

    private final Grid<Row> grid = new Grid<>();

    public UserListView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Пользователи";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Пользователи");
        title.getStyle().set("margin", "0");
        Span lead = new Span("Роль «проверяющий» назначается пользователю. Отдельного справочника проверяющих нет.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button create = new Button("Создать преподавателя", e -> TeacherDialogs.createTeacher(provisioning, this::reload));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setId("createTeacher");
        Button assign = new Button("Назначить роли", e -> assignSelected());
        assign.setId("assignRoles");
        Button password = new Button("Сменить пароль", e -> changePasswordSelected());
        password.setId("changePassword");
        Button refresh = new Button("Обновить", e -> reload());
        refresh.setId("refreshUsers");

        grid.setId("usersGrid");
        grid.setWidthFull();
        grid.setSelectionMode(Grid.SelectionMode.SINGLE);
        grid.addColumn(Row::name).setHeader("Имя").setFlexGrow(2);
        grid.addColumn(Row::username).setHeader("Логин").setFlexGrow(1);
        grid.addColumn(Row::roles).setHeader("Роли").setFlexGrow(3);
        grid.addColumn(row -> row.active() ? "да" : "нет").setHeader("Активен").setWidth("8em").setFlexGrow(0);

        getContent().add(title, lead, new HorizontalLayout(create, assign, password, refresh), grid);
        getContent().expand(grid);
        reload();
    }

    private void assignSelected() {
        Row row = grid.getSelectedItems().stream().findFirst().orElse(null);
        if (row == null) {
            Notification.show("Выберите пользователя", 3000, Notification.Position.MIDDLE);
            return;
        }
        TeacherDialogs.assignRoles(provisioning, access, row.username(), row.name(), this::reload);
    }

    private void changePasswordSelected() {
        Row row = grid.getSelectedItems().stream().findFirst().orElse(null);
        if (row == null) {
            Notification.show("Выберите пользователя", 3000, Notification.Position.MIDDLE);
            return;
        }
        TeacherDialogs.changePassword(userManager, row.username(), row.name());
    }

    private void reload() {
        Map<String, List<String>> roles = new HashMap<>();
        for (RoleAssignmentEntity assignment : dataManager.load(RoleAssignmentEntity.class).all().list()) {
            if (!UserProvisioning.RESOURCE.equals(assignment.getRoleType())
                    && !UserProvisioning.ROW_LEVEL.equals(assignment.getRoleType())) continue;
            roles.computeIfAbsent(assignment.getUsername(), key -> new ArrayList<>()).add(assignment.getRoleCode());
        }
        List<Row> rows = new ArrayList<>();
        for (User user : dataManager.load(User.class).all()
                .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName").add("active"))
                .list()) {
            String labels = roles.getOrDefault(user.getUsername(), List.of()).stream()
                    .sorted()
                    .map(TeacherDialogs::label)
                    .collect(Collectors.joining(", "));
            rows.add(new Row(user.getUsername(), user.getDisplayName(), labels, Boolean.TRUE.equals(user.getActive())));
        }
        rows.sort(Comparator.comparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        grid.setItems(rows);
    }

    private record Row(String username, String name, String roles, boolean active) {
    }
}
