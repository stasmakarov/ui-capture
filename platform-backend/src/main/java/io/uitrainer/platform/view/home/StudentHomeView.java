package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

/** Активные группы ученика и открытые в них курсы. Портала нет. */
@Route(value = "cabinet/student", layout = MainView.class)
@ViewController(id = PlatformViews.STUDENT_HOME)
public class StudentHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private GroupProgressService groupProgress;

    public StudentHomeView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Моё обучение";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Моё обучение");
        title.getStyle().set("margin", "0");
        Span empty = new Span("Пока нет активных групп.");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Grid<GroupProgressService.GroupSummary> grid = new Grid<>();
        grid.setId("studentGroups");
        grid.setWidthFull();
        grid.addColumn(row -> row.group().getName()).setHeader("Группа").setFlexGrow(2);
        grid.addColumn(GroupProgressService.GroupSummary::progressLabel).setHeader("Прогресс").setFlexGrow(1);
        grid.addItemClickListener(e -> getUI().ifPresent(ui -> ui.navigate(
                StudentGroupView.class, new RouteParameters("groupId", e.getItem().group().getId().toString()))));
        User student = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .one();
        List<StudyGroup> groups = dataManager.load(StudyGroup.class)
                .query("select g from uic_StudyGroup g join g.members m where m.student.username = :u and g.status = :st order by g.name")
                .parameter("u", access.username())
                .parameter("st", GroupStatus.ACTIVE.getId())
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE))
                .list();
        for (StudyGroup group : groups) {
            groupProgress.recalculateStudentInGroup(group, student);
        }
        empty.setVisible(groups.isEmpty());
        grid.setItems(groups.stream().map(g -> groupProgress.summarizeStudent(g, student)).toList());
        getContent().add(title, empty, grid);
        getContent().expand(grid);
    }
}
