package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/** Курсы одного преподавателя. Директор открывает их только для чтения, администратор может править. */
@Route(value = "cabinet/teachers/:username", layout = MainView.class)
@ViewController(id = PlatformViews.TEACHER_COURSES)
public class TeacherCoursesView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private CurrentUserAccess access;

    private String username = "";
    private String heading = "Курсы преподавателя";
    private final H2 title = new H2();
    private final Span note = new Span();
    private final Span empty = new Span();
    private final Grid<Course> grid = new Grid<>(Course.class, false);
    private final Grid<StudyGroup> groupsGrid = new Grid<>(StudyGroup.class, false);
    private final Checkbox includeClosed = new Checkbox("Включая закрытые");
    private final Span groupsEmpty = new Span();

    public TeacherCoursesView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
        title.getStyle().set("margin", "0");
        note.getStyle().set("color", "var(--lumo-secondary-text-color)");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        grid.setId("teacherCourses");
        grid.setWidthFull();
        grid.addColumn(Course::getTitle).setHeader("Курс").setFlexGrow(2);
        grid.addColumn(Course::getCode).setHeader("Код").setFlexGrow(1);
        grid.addItemClickListener(e -> viewNavigators.detailView(this, Course.class)
                .editEntity(e.getItem())
                .withBackwardNavigation(true)
                .navigate());
        groupsGrid.setId("teacherGroups");
        groupsGrid.setWidthFull();
        groupsGrid.addColumn(StudyGroup::getName).setHeader("Группа").setFlexGrow(2);
        groupsGrid.addColumn(g -> g.getStatus() == null ? "" : g.getStatus().getId()).setHeader("Статус").setFlexGrow(1);
        groupsGrid.addItemClickListener(e -> viewNavigators.detailView(this, StudyGroup.class)
                .editEntity(e.getItem())
                .withBackwardNavigation(true)
                .navigate());
        includeClosed.setId("includeClosedGroups");
        includeClosed.addValueChangeListener(e -> reloadGroups());
        groupsEmpty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Button back = new Button("К кабинету", e -> getUI().ifPresent(ui -> HomeViews.openLanding(ui, access)));
        back.setId("backToCabinet");
        H3 coursesTitle = new H3("Курсы");
        coursesTitle.getStyle().set("margin", "0");
        H3 groupsTitle = new H3("Группы");
        groupsTitle.getStyle().set("margin", "0");
        getContent().add(title, note, back, coursesTitle, empty, grid, groupsTitle, includeClosed, groupsEmpty, groupsGrid);
        getContent().expand(grid);
    }

    @Override
    public String getPageTitle() {
        return heading;
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        username = event.getRouteParameters().get("username").orElse("");
        super.beforeEnter(event);
        boolean trainer = dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :code and r.roleType = 'resource'")
                .parameter("u", username)
                .parameter("code", TrainerRole.CODE)
                .optional()
                .isPresent();
        User user = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", username)
                .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                .optional()
                .orElse(null);
        if (!trainer || user == null) {
            heading = "Преподаватель не найден";
            title.setText(heading);
            note.setText("");
            empty.setText("Это не преподаватель.");
            empty.setVisible(true);
            grid.setVisible(false);
            return;
        }
        heading = user.getDisplayName();
        title.setText(heading);
        boolean readOnly = !access.isAdmin() && !access.isTrainer();
        note.setText(readOnly ? "Только просмотр." : "Курс можно открыть и поправить.");
        List<Course> courses = dataManager.load(Course.class)
                .query("select c from uic_Course c where c.owner.username = :u order by c.title")
                .parameter("u", username)
                .fetchPlan("_base")
                .list();
        empty.setText("Курсов нет.");
        empty.setVisible(courses.isEmpty());
        grid.setVisible(true);
        grid.setItems(courses);
        reloadGroups();
    }

    private void reloadGroups() {
        if (username == null || username.isBlank()) {
            return;
        }
        boolean closed = Boolean.TRUE.equals(includeClosed.getValue());
        List<StudyGroup> groups;
        if (closed) {
            groups = dataManager.load(StudyGroup.class)
                    .query("select g from uic_StudyGroup g where g.owner.username = :u order by g.name")
                    .parameter("u", username)
                    .fetchPlan("_base")
                    .list();
        } else {
            groups = dataManager.load(StudyGroup.class)
                    .query("select g from uic_StudyGroup g where g.owner.username = :u and g.status <> :closed order by g.name")
                    .parameter("u", username)
                    .parameter("closed", GroupStatus.CLOSED.getId())
                    .fetchPlan("_base")
                    .list();
        }
        groupsEmpty.setText("Групп нет.");
        groupsEmpty.setVisible(groups.isEmpty());
        groupsGrid.setItems(groups);
    }
}
