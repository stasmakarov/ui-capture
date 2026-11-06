package io.uicapture.platform.view.course;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uicapture.platform.entity.Course;
import io.uicapture.platform.security.PlatformViews;
import io.uicapture.platform.service.CurrentUserAccess;
import io.uicapture.platform.view.main.MainView;
import io.uicapture.platform.view.studio.StudioLaunch;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;

@Route(value = "courses", layout = MainView.class)
@ViewController(id = PlatformViews.COURSE_LIST)
@ViewDescriptor(path = "course-list-view.xml")
@LookupComponent("coursesDataGrid")
@DialogMode(width = "64em")
public class CourseListView extends StandardListView<Course> {

    @Autowired
    private StudioLaunch studioLaunch;
    @Autowired
    private CurrentUserAccess access;

    @ViewComponent
    private DataGrid<Course> coursesDataGrid;
    @ViewComponent
    private JmixButton recordInStudio;
    @ViewComponent
    private JmixButton recordInStudioLocal;

    @Subscribe
    public void onInit(InitEvent event) {
        boolean canRecord = access.isTrainer() || access.isAdmin();
        recordInStudio.setVisible(canRecord);
        recordInStudioLocal.setVisible(canRecord);
        recordInStudio.addClickListener(e -> record(false));
        recordInStudioLocal.addClickListener(e -> record(true));
    }

    private void record(boolean forceFallback) {
        Set<Course> selected = coursesDataGrid.getSelectedItems();
        if (selected.size() != 1) {
            Notification.show("Выберите курс, для которого записывается урок", 4000, Notification.Position.MIDDLE);
            return;
        }
        studioLaunch.launch(this, forceFallback, selected.iterator().next().getId());
    }
}
