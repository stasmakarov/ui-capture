package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import io.jmix.core.DataManager;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.TrainerRole;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/** Преподаватели — пользователи с ролью тренера — и число их курсов. Групп в системе нет. */
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
        List<TeacherRow> rows = new ArrayList<>();
        for (String username : usernames) {
            User user = dataManager.load(User.class)
                    .query("select u from User u where u.username = :u")
                    .parameter("u", username)
                    .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                    .optional()
                    .orElse(null);
            if (user == null) continue;
            Long courses = dataManager.loadValue(
                            "select count(c) from uic_Course c where c.owner.username = :u", Long.class)
                    .parameter("u", username)
                    .one();
            rows.add(new TeacherRow(user.getUsername(), user.getDisplayName(), courses == null ? 0 : courses));
        }
        rows.sort(Comparator.comparing(TeacherRow::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public record TeacherRow(String username, String name, long courses) {
    }
}
