package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

/** Кабинет директора: все преподаватели и число курсов. Без ролей, токенов и записи. */
@Route(value = "cabinet/director", layout = MainView.class)
@ViewController(id = PlatformViews.DIRECTOR_HOME)
public class DirectorHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;

    public DirectorHomeView() {
        getContent().setWidthFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Преподаватели";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Преподаватели");
        title.getStyle().set("margin", "0");
        Span lead = new Span("Все преподаватели и число их курсов. Откройте строку, чтобы посмотреть курсы.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");
        TeacherList teachers = new TeacherList(dataManager, row -> getUI().ifPresent(ui -> ui.navigate(
                TeacherCoursesView.class, new RouteParameters("username", row.username()))));
        teachers.reload();
        getContent().add(title, lead, teachers);
    }
}
