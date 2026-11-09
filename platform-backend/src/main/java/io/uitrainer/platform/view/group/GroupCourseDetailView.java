package io.uitrainer.platform.view.group;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;

@Route(value = "group-courses/:id", layout = MainView.class)
@ViewController(id = PlatformViews.GROUP_COURSE_DETAIL)
@ViewDescriptor(path = "group-course-detail-view.xml")
@EditedEntityContainer("groupCourseDc")
@DialogMode(width = "32em")
public class GroupCourseDetailView extends StandardDetailView<GroupCourse> {
}
