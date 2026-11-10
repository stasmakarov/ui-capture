package io.uitrainer.platform.view.group;

import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.SortOrders;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "group-courses/:id", layout = MainView.class)
@ViewController(id = PlatformViews.GROUP_COURSE_DETAIL)
@ViewDescriptor(path = "group-course-detail-view.xml")
@EditedEntityContainer("groupCourseDc")
@DialogMode(width = "32em")
public class GroupCourseDetailView extends StandardDetailView<GroupCourse> {

    @Autowired
    private EntityStates entityStates;

    @ViewComponent
    private EntityComboBox<Course> courseField;

    @Subscribe
    public void onInitEntity(InitEntityEvent<GroupCourse> event) {
        GroupCourse line = event.getEntity();
        StudyGroup group = line.getGroup();
        if (group == null || group.getCourses() == null) {
            line.setSortOrder(1);
            return;
        }
        line.setSortOrder(SortOrders.next(group.getCourses().stream()
                .filter(gc -> gc != line)
                .map(GroupCourse::getSortOrder)
                .toList()));
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            courseField.setReadOnly(true);
        }
    }
}
