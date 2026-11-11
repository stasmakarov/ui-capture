package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.core.DataManager;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.View;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.DirectorRole;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.user.TeacherDialogs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Преподаватели и директора в кабинете платформы. */
public class StaffList extends VerticalLayout {

    private final DataManager dataManager;
    private final UserProvisioning provisioning;
    private final CurrentUserAccess access;
    private final ViewNavigators viewNavigators;
    private final View<?> host;
    private final Grid<StaffRow> grid = new Grid<>();
    private final Span empty = new Span("Пока нет преподавателей и директоров.");

    public StaffList(DataManager dataManager, UserProvisioning provisioning, CurrentUserAccess access,
                     ViewNavigators viewNavigators, View<?> host) {
        this.dataManager = dataManager;
        this.provisioning = provisioning;
        this.access = access;
        this.viewNavigators = viewNavigators;
        this.host = host;
        setPadding(false);
        setSpacing(true);
        setWidthFull();
        empty.setVisible(false);
        grid.setId("staffGrid");
        grid.setWidthFull();
        grid.setMinHeight("12em");
        grid.addColumn(StaffRow::name).setHeader("ФИО").setFlexGrow(2);
        grid.addColumn(StaffRow::username).setHeader("Логин").setFlexGrow(1);
        grid.addColumn(StaffRow::role).setHeader("Роль").setFlexGrow(1);
        grid.addColumn(StaffRow::courses).setHeader("Курсы").setWidth("8em").setFlexGrow(0);
        grid.addColumn(StaffRow::groups).setHeader("Группы").setWidth("8em").setFlexGrow(0);
        grid.addColumn(row -> row.active() ? "да" : "нет").setHeader("Активен").setWidth("8em").setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        Button create = new Button("Создать", e -> TeacherDialogs.createStaff(provisioning, this::reload));
        create.addThemeVariants(com.vaadin.flow.component.button.ButtonVariant.LUMO_PRIMARY);
        create.setId("createStaff");
        add(create, empty, grid);
    }

    public void reload() {
        List<StaffRow> rows = load();
        empty.setVisible(rows.isEmpty());
        grid.setItems(rows);
    }

    private HorizontalLayout actions(StaffRow row) {
        Button card = new Button("Карточка", e -> {
            User user = dataManager.load(User.class)
                    .query("select u from User u where u.username = :u")
                    .parameter("u", row.username())
                    .one();
            viewNavigators.detailView(host, User.class).editEntity(user).withBackwardNavigation(true).navigate();
        });
        card.setId("openUser-" + row.username());
        boolean self = row.username().equals(access.username());
        Button lock = new Button(row.active() ? "Заблокировать" : "Разблокировать", e -> {
            if (self) {
                return;
            }
            provisioning.setActive(row.username(), !row.active());
            reload();
        });
        lock.setEnabled(!self);
        lock.setId("toggleActive-" + row.username());
        return new HorizontalLayout(card, lock);
    }

    private List<StaffRow> load() {
        Set<String> usernames = new HashSet<>();
        usernames.addAll(usernamesWith(TrainerRole.CODE));
        usernames.addAll(usernamesWith(DirectorRole.CODE));
        Map<String, Long> courses = counts(
                "select c.owner.username, count(c) from uic_Course c group by c.owner.username");
        Map<String, Long> groups = counts(
                "select g.owner.username, count(g) from uic_StudyGroup g where g.status = :st group by g.owner.username",
                GroupStatus.ACTIVE.getId());
        List<StaffRow> rows = new ArrayList<>();
        for (String username : usernames) {
            User user = dataManager.load(User.class)
                    .query("select u from User u where u.username = :u")
                    .parameter("u", username)
                    .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName").add("active"))
                    .optional()
                    .orElse(null);
            if (user == null) {
                continue;
            }
            Set<String> roles = provisioning.resourceRoles(username);
            String role = roles.contains(TrainerRole.CODE) && roles.contains(DirectorRole.CODE)
                    ? "Преподаватель, директор"
                    : roles.contains(DirectorRole.CODE) ? "Директор" : "Преподаватель";
            rows.add(new StaffRow(user.getUsername(), user.getDisplayName(), role,
                    courses.getOrDefault(username, 0L), groups.getOrDefault(username, 0L),
                    Boolean.TRUE.equals(user.getActive())));
        }
        rows.sort(Comparator.comparing(StaffRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private List<String> usernamesWith(String role) {
        return dataManager.loadValue(
                        "select distinct r.username from sec_RoleAssignmentEntity r "
                                + "where r.roleCode = :code and r.roleType = 'resource'", String.class)
                .parameter("code", role)
                .list();
    }

    private Map<String, Long> counts(String query) {
        return counts(query, null);
    }

    private Map<String, Long> counts(String query, String status) {
        var loader = dataManager.loadValues(query).properties("username", "cnt");
        if (status != null) {
            loader = loader.parameter("st", status);
        }
        Map<String, Long> result = new HashMap<>();
        for (KeyValueEntity row : loader.list()) {
            result.put(row.getValue("username"), ((Number) row.getValue("cnt")).longValue());
        }
        return result;
    }

    public record StaffRow(String username, String name, String role, long courses, long groups, boolean active) {
    }
}
