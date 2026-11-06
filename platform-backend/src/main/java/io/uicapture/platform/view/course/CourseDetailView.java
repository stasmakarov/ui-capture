package io.uicapture.platform.view.course;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uicapture.platform.entity.Course;
import io.uicapture.platform.entity.Lesson;
import io.uicapture.platform.entity.User;
import io.uicapture.platform.security.PlatformViews;
import io.uicapture.platform.service.CurrentUserAccess;
import io.uicapture.platform.view.main.MainView;
import io.uicapture.platform.view.studio.StudioLaunch;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "courses/:id", layout = MainView.class)
@ViewController(id = PlatformViews.COURSE_DETAIL)
@ViewDescriptor(path = "course-detail-view.xml")
@EditedEntityContainer("courseDc")
public class CourseDetailView extends StandardDetailView<Course> {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private StudioLaunch studioLaunch;
    @Autowired
    private CurrentUserAccess access;

    @ViewComponent
    private JmixButton recordInStudio;
    @ViewComponent
    private JmixButton recordInStudioLocal;
    @ViewComponent
    private DataGrid<Lesson> lessonsDataGrid;
    @ViewComponent
    private EntityComboBox<User> ownerField;

    @Subscribe
    public void onInit(InitEvent event) {
        boolean canRecord = access.isTrainer() || access.isAdmin();
        recordInStudio.setVisible(canRecord);
        recordInStudioLocal.setVisible(canRecord);
        recordInStudio.addClickListener(e -> record(false));
        recordInStudioLocal.addClickListener(e -> record(true));
    }

    @Subscribe
    public void onInitEntity(InitEntityEvent<Course> event) {
        event.getEntity().setOwner(currentUser());
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        boolean editor = access.isAdmin() || access.isTrainer();
        if (!editor) {
            setReadOnly(true);
            hideLessonMutations();
            return;
        }
        if (!access.isAdmin()) {
            ownerField.setReadOnly(true);
        }
    }

    private void hideLessonMutations() {
        for (String id : new String[]{"createAction", "editAction", "removeAction"}) {
            Action action = lessonsDataGrid.getAction(id);
            if (action != null) action.setVisible(false);
        }
    }

    private void record(boolean forceFallback) {
        Course course = getEditedEntity();
        if (entityStates.isNew(course)) {
            Notification.show("Сначала сохраните курс — запись привязывается к нему", 4000, Notification.Position.MIDDLE);
            return;
        }
        studioLaunch.launch(this, forceFallback, course.getId());
    }

    private User currentUser() {
        return dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                .one();
    }
}
