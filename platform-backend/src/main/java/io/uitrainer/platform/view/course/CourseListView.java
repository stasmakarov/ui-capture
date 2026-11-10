package io.uitrainer.platform.view.course;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CourseAccessService;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.studio.StudioLaunch;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    @Autowired
    private CourseAccessService courseAccess;

    @ViewComponent
    private CollectionContainer<Course> coursesDc;
    @ViewComponent
    private DataGrid<Course> coursesDataGrid;
    @ViewComponent
    private JmixButton createButton;
    @ViewComponent
    private JmixButton editButton;
    @ViewComponent
    private JmixButton removeButton;
    @ViewComponent
    private JmixButton recordInStudio;
    @ViewComponent
    private JmixButton recordInStudioLocal;

    private boolean readOnlyHintAdded;
    private Map<UUID, String> roleByCourse = new HashMap<>();

    @Subscribe
    public void onInit(InitEvent event) {
        boolean editor = access.isTrainer() || access.isAdmin();
        recordInStudio.setVisible(editor);
        recordInStudioLocal.setVisible(editor);
        recordInStudio.addClickListener(e -> record(false));
        recordInStudioLocal.addClickListener(e -> record(true));
        createButton.setVisible(editor);
        removeButton.setVisible(editor);
        Action create = coursesDataGrid.getAction("createAction");
        Action remove = coursesDataGrid.getAction("removeAction");
        if (create != null) create.setVisible(editor);
        if (remove != null) remove.setVisible(editor);
        refreshRoles();
        coursesDataGrid.addColumn(c -> roleByCourse.getOrDefault(c.getId(), "")).setHeader("Роль").setKey("accessRole");
        if (!editor && !readOnlyHintAdded) {
            readOnlyHintAdded = true;
            editButton.setText("Открыть");
            Span hint = new Span("Справочник курсов. Изменять курсы и записывать уроки нельзя.");
            hint.setId("coursesReadonlyHint");
            hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
            getContent().addComponentAtIndex(0, hint);
        }
        coursesDataGrid.addSelectionListener(e -> refreshSelectionActions());
        refreshSelectionActions();
    }

    private void refreshSelectionActions() {
        Set<Course> selected = coursesDataGrid.getSelectedItems();
        boolean one = selected.size() == 1;
        Course course = one ? selected.iterator().next() : null;
        boolean edit = course != null && courseAccess.canEdit(course);
        boolean manage = course != null && courseAccess.canManageAccess(course);
        recordInStudio.setVisible((access.isTrainer() || access.isAdmin()) && edit);
        recordInStudioLocal.setVisible((access.isTrainer() || access.isAdmin()) && edit);
        removeButton.setEnabled(manage);
        Action remove = coursesDataGrid.getAction("removeAction");
        if (remove != null) {
            remove.setEnabled(manage);
        }
    }

    private void record(boolean forceFallback) {
        Set<Course> selected = coursesDataGrid.getSelectedItems();
        if (selected.size() != 1) {
            Notification.show("Выберите курс, для которого записывается урок", 4000, Notification.Position.MIDDLE);
            return;
        }
        Course course = selected.iterator().next();
        if (!courseAccess.canEdit(course)) {
            Notification.show("Курс недоступен для записи", 4000, Notification.Position.MIDDLE);
            return;
        }
        studioLaunch.launch(this, forceFallback, course.getId());
    }

    @Subscribe(id = "coursesDc", target = Target.DATA_CONTAINER)
    public void onCoursesDcCollectionChange(CollectionContainer.CollectionChangeEvent<Course> event) {
        refreshRoles();
    }

    private void refreshRoles() {
        roleByCourse = courseAccess.roleLabels(coursesDc.getItems());
    }
}
