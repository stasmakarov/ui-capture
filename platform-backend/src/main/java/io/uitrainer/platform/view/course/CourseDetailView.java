package io.uitrainer.platform.view.course;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionChangeType;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.LessonOrderService;
import io.uitrainer.platform.service.SortOrders;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.studio.StudioLaunch;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

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
    @Autowired
    private LessonOrderService lessonOrder;

    @ViewComponent
    private JmixButton recordInStudio;
    @ViewComponent
    private JmixButton recordInStudioLocal;
    @ViewComponent
    private JmixButton saveAndCloseButton;
    @ViewComponent
    private DataGrid<Lesson> lessonsDataGrid;
    @ViewComponent
    private CollectionContainer<Lesson> lessonsDc;
    @ViewComponent
    private EntityComboBox<User> ownerField;
    @ViewComponent
    private JmixButton moveUpButton;
    @ViewComponent
    private JmixButton moveDownButton;

    private boolean rearrangingLessons;

    @Subscribe
    public void onInit(InitEvent event) {
        boolean canRecord = access.isTrainer() || access.isAdmin();
        recordInStudio.setVisible(canRecord);
        recordInStudioLocal.setVisible(canRecord);
        recordInStudio.addClickListener(e -> record(false));
        recordInStudioLocal.addClickListener(e -> record(true));
        moveUpButton.addClickListener(e -> moveLesson(-1));
        moveDownButton.addClickListener(e -> moveLesson(1));
        lessonsDataGrid.addSelectionListener(e -> refreshMoveButtons());
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
            saveAndCloseButton.setVisible(false);
            hideLessonMutations();
            return;
        }
        if (!access.isAdmin()) {
            ownerField.setReadOnly(true);
        }
        refreshMoveButtons();
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        List<Lesson> ordered = SortOrders.sorted(lessonsDc.getMutableItems(), Lesson::getSortOrder);
        SortOrders.normalize(ordered, Lesson::setSortOrder);
        replaceLessons(ordered);
    }

    @Subscribe(id = "lessonsDc", target = Target.DATA_CONTAINER)
    public void onLessonsDcCollectionChange(CollectionContainer.CollectionChangeEvent<Lesson> event) {
        if (rearrangingLessons) {
            return;
        }
        if (event.getChangeType() == CollectionChangeType.REMOVE_ITEMS) {
            List<Lesson> ordered = SortOrders.sorted(lessonsDc.getMutableItems(), Lesson::getSortOrder);
            SortOrders.normalize(ordered, Lesson::setSortOrder);
            replaceLessons(ordered);
        }
        refreshMoveButtons();
    }

    private void moveLesson(int delta) {
        Lesson selected = lessonsDataGrid.getSingleSelectedItem();
        if (selected == null) {
            return;
        }
        List<Lesson> ordered = SortOrders.sorted(lessonsDc.getMutableItems(), Lesson::getSortOrder);
        if (!lessonOrder.reorder(ordered, selected, delta)) {
            return;
        }
        replaceLessons(ordered);
        lessonsDataGrid.select(selected);
        refreshMoveButtons();
    }

    private void replaceLessons(List<Lesson> ordered) {
        rearrangingLessons = true;
        try {
            List<Lesson> mutable = lessonsDc.getMutableItems();
            mutable.clear();
            mutable.addAll(ordered);
        } finally {
            rearrangingLessons = false;
        }
    }

    private void refreshMoveButtons() {
        Lesson selected = lessonsDataGrid.getSingleSelectedItem();
        List<Lesson> ordered = selected == null
                ? List.of()
                : SortOrders.sorted(new ArrayList<>(lessonsDc.getItems()), Lesson::getSortOrder);
        int index = selected == null ? -1 : ordered.indexOf(selected);
        boolean editor = access.isAdmin() || access.isTrainer();
        moveUpButton.setEnabled(editor && index > 0);
        moveDownButton.setEnabled(editor && index >= 0 && index < ordered.size() - 1);
    }

    private void hideLessonMutations() {
        for (String id : new String[]{"createAction", "editAction", "removeAction"}) {
            Action action = lessonsDataGrid.getAction(id);
            if (action != null) {
                action.setVisible(false);
                action.setEnabled(false);
            }
        }
        moveUpButton.setVisible(false);
        moveDownButton.setVisible(false);
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
