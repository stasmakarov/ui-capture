package io.uitrainer.platform.view.course;

import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.CourseSharedWith;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "course-shared-with/:id", layout = MainView.class)
@ViewController(id = PlatformViews.COURSE_SHARED_WITH_DETAIL)
@ViewDescriptor(path = "course-shared-with-detail-view.xml")
@EditedEntityContainer("courseSharedWithDc")
@DialogMode(width = "32em")
public class CourseSharedWithDetailView extends StandardDetailView<CourseSharedWith> {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EntityStates entityStates;
    @ViewComponent
    private CollectionContainer<User> trainersDc;
    @ViewComponent
    private EntityComboBox<User> trainerField;

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            trainerField.setReadOnly(true);
        }
        trainersDc.setItems(dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username in "
                        + "(select r.username from sec_RoleAssignmentEntity r where r.roleCode = :c and r.roleType = 'resource') "
                        + "order by u.username")
                .parameter("c", TrainerRole.CODE)
                .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                .list());
    }
}
