package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import io.jmix.core.DataManager;
import io.jmix.core.entity.KeyValueEntity;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.View;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.StudentRole;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.user.TeacherDialogs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Студенты в кабинете платформы. */
public class StudentList extends VerticalLayout {

    private final DataManager dataManager;
    private final UserProvisioning provisioning;
    private final CurrentUserAccess access;
    private final ViewNavigators viewNavigators;
    private final View<?> host;
    private final Grid<StudentRow> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span empty = new Span("Студентов пока нет.");
    private List<StudentRow> all = List.of();

    public StudentList(DataManager dataManager, UserProvisioning provisioning, CurrentUserAccess access,
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
        search.setPlaceholder("Поиск по ФИО или логину");
        search.setId("studentSearch");
        search.setWidth("24em");
        search.setClearButtonVisible(true);
        search.addValueChangeListener(e -> applyFilter());
        Button create = new Button("Создать студента", e -> TeacherDialogs.createStudent(provisioning, this::reload));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setId("createStudent");
        grid.setId("studentsGrid");
        grid.setWidthFull();
        grid.setMinHeight("12em");
        grid.addColumn(StudentRow::name).setHeader("ФИО").setFlexGrow(2);
        grid.addColumn(StudentRow::username).setHeader("Логин").setFlexGrow(1);
        grid.addColumn(StudentRow::groups).setHeader("Группы").setFlexGrow(2);
        grid.addColumn(row -> row.active() ? "да" : "нет").setHeader("Активен").setWidth("8em").setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        add(new HorizontalLayout(create, search), empty, grid);
    }

    public void reload() {
        all = load();
        applyFilter();
    }

    private void applyFilter() {
        String q = search.getValue() == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<StudentRow> rows = q.isEmpty() ? all : all.stream()
                .filter(row -> row.name().toLowerCase(Locale.ROOT).contains(q)
                        || row.username().toLowerCase(Locale.ROOT).contains(q))
                .toList();
        empty.setVisible(rows.isEmpty());
        grid.setItems(rows);
    }

    private HorizontalLayout actions(StudentRow row) {
        Button card = new Button("Карточка", e -> {
            User user = dataManager.load(User.class)
                    .query("select u from User u where u.username = :u")
                    .parameter("u", row.username())
                    .one();
            viewNavigators.detailView(host, User.class).editEntity(user).withBackwardNavigation(true).navigate();
        });
        boolean self = row.username().equals(access.username());
        Button lock = new Button(row.active() ? "Заблокировать" : "Разблокировать", e -> {
            if (self) {
                return;
            }
            provisioning.setActive(row.username(), !row.active());
            reload();
        });
        lock.setEnabled(!self);
        return new HorizontalLayout(card, lock);
    }

    private List<StudentRow> load() {
        List<String> usernames = dataManager.loadValue(
                        "select distinct r.username from sec_RoleAssignmentEntity r "
                                + "where r.roleCode = :code and r.roleType = 'resource'", String.class)
                .parameter("code", StudentRole.CODE)
                .list();
        Map<String, String> groups = new HashMap<>();
        for (KeyValueEntity row : dataManager.loadValues(
                        "select m.student.username, m.group.name from uic_GroupMember m")
                .properties("username", "groupName")
                .list()) {
            String username = row.getValue("username");
            String groupName = row.getValue("groupName");
            groups.merge(username, groupName, (a, b) -> a + ", " + b);
        }
        List<StudentRow> rows = new ArrayList<>();
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
            rows.add(new StudentRow(user.getUsername(), user.getDisplayName(),
                    groups.getOrDefault(username, "—"), Boolean.TRUE.equals(user.getActive())));
        }
        rows.sort(Comparator.comparing(StudentRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public record StudentRow(String username, String name, String groups, boolean active) {
    }
}
