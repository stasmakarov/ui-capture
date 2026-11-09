package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.core.DataManager;
import io.jmix.core.entity.KeyValueEntity;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.TrainerRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Преподаватели — пользователи с ролью тренера — и число их курсов и активных групп. */
public class TeacherList extends VerticalLayout {

    private final DataManager dataManager;
    private final Grid<TeacherRow> grid = new Grid<>();
    private final Span empty = new Span("Преподавателей пока нет.");

    public TeacherList(DataManager dataManager, Consumer<TeacherRow> onOpen) {
        this.dataManager = dataManager;
        setPadding(false);
        setSpacing(true);
        setWidthFull();
        empty.setVisible(false);
        grid.setId("teachersGrid");
        grid.setWidthFull();
        grid.setMinHeight("12em");
        grid.addColumn(TeacherRow::name).setHeader("Преподаватель").setFlexGrow(2);
        grid.addColumn(TeacherRow::username).setHeader("Логин").setFlexGrow(1);
        grid.addColumn(TeacherRow::courses).setHeader("Курсы").setWidth("8em").setFlexGrow(0);
        grid.addColumn(TeacherRow::activeGroups).setHeader("Группы").setWidth("8em").setFlexGrow(0);
        grid.addItemClickListener(event -> onOpen.accept(event.getItem()));
        add(empty, grid);
    }

    public void reload() {
        List<TeacherRow> rows = load();
        empty.setVisible(rows.isEmpty());
        grid.setItems(rows);
    }

    private List<TeacherRow> load() {
        List<String> usernames = dataManager.loadValue(
                        "select distinct r.username from sec_RoleAssignmentEntity r "
                                + "where r.roleCode = :code and r.roleType = 'resource'", String.class)
                .parameter("code", TrainerRole.CODE)
                .list();
        Map<String, Long> courses = counts(
                "select c.owner.username, count(c) from uic_Course c group by c.owner.username");
        Map<String, Long> groups = counts(
                "select g.owner.username, count(g) from uic_StudyGroup g where g.status = :st group by g.owner.username",
                GroupStatus.ACTIVE.getId());
        List<TeacherRow> rows = new ArrayList<>();
        for (String username : usernames) {
            User user = dataManager.load(User.class)
                    .query("select u from User u where u.username = :u")
                    .parameter("u", username)
                    .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                    .optional()
                    .orElse(null);
            if (user == null) continue;
            rows.add(new TeacherRow(user.getUsername(), user.getDisplayName(),
                    courses.getOrDefault(username, 0L), groups.getOrDefault(username, 0L)));
        }
        rows.sort(Comparator.comparing(TeacherRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
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

    public record TeacherRow(String username, String name, long courses, long activeGroups) {
    }
}
