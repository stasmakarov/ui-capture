package io.uitrainer.platform.view.group;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionChangeType;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.Target;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.GroupLifecycleService;
import io.uitrainer.platform.service.SortOrders;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Route(value = "groups/:id", layout = MainView.class)
@ViewController(id = PlatformViews.GROUP_DETAIL)
@ViewDescriptor(path = "study-group-detail-view.xml")
@EditedEntityContainer("studyGroupDc")
public class StudyGroupDetailView extends StandardDetailView<StudyGroup> {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private GroupLifecycleService lifecycle;

    @ViewComponent
    private JmixButton activateButton;
    @ViewComponent
    private JmixButton closeGroupButton;
    @ViewComponent
    private JmixButton saveAndCloseButton;
    @ViewComponent
    private DataGrid<GroupMember> membersDataGrid;
    @ViewComponent
    private DataGrid<GroupCourse> coursesDataGrid;
    @ViewComponent
    private CollectionContainer<GroupCourse> coursesDc;
    @ViewComponent
    private InstanceContainer<StudyGroup> studyGroupDc;
    @ViewComponent
    private JmixButton moveUpButton;
    @ViewComponent
    private JmixButton moveDownButton;

    private boolean rearrangingCourses;

    @Subscribe
    public void onInit(InitEvent event) {
        activateButton.addClickListener(e -> changeStatus(true));
        closeGroupButton.addClickListener(e -> changeStatus(false));
        moveUpButton.addClickListener(e -> moveCourse(-1));
        moveDownButton.addClickListener(e -> moveCourse(1));
        coursesDataGrid.addSelectionListener(e -> refreshMoveButtons());
    }

    @Subscribe
    public void onInitEntity(InitEntityEvent<StudyGroup> event) {
        event.getEntity().setOwner(currentUser());
        event.getEntity().setStatus(GroupStatus.CREATED);
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        boolean editor = access.isAdmin() || access.isTrainer();
        if (!editor) {
            setReadOnly(true);
            saveAndCloseButton.setVisible(false);
            hideMutations();
        }
        refreshLifecycleButtons();
        refreshMoveButtons();
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        List<GroupCourse> ordered = SortOrders.sorted(coursesDc.getMutableItems(), GroupCourse::getSortOrder);
        SortOrders.normalize(ordered, GroupCourse::setSortOrder);
        replaceCourses(ordered);
    }

    @Subscribe(id = "coursesDc", target = Target.DATA_CONTAINER)
    public void onCoursesDcCollectionChange(CollectionContainer.CollectionChangeEvent<GroupCourse> event) {
        if (rearrangingCourses) {
            return;
        }
        if (event.getChangeType() == CollectionChangeType.REMOVE_ITEMS) {
            List<GroupCourse> ordered = SortOrders.sorted(coursesDc.getMutableItems(), GroupCourse::getSortOrder);
            SortOrders.normalize(ordered, GroupCourse::setSortOrder);
            replaceCourses(ordered);
        }
        refreshMoveButtons();
    }

    private void changeStatus(boolean activate) {
        StudyGroup group = getEditedEntity();
        if (entityStates.isNew(group)) {
            Notification.show("Сначала сохраните группу", 4000, Notification.Position.MIDDLE);
            return;
        }
        try {
            StudyGroup saved = activate ? lifecycle.activate(group.getId()) : lifecycle.close(group.getId());
            studyGroupDc.setItem(saved);
            refreshLifecycleButtons();
            Notification.show(activate ? "Группа активна" : "Группа закрыта", 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } catch (ConflictException ex) {
            Notification.show(ex.getMessage(), 4000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void refreshLifecycleButtons() {
        StudyGroup group = getEditedEntity();
        boolean editor = access.isAdmin() || access.isTrainer();
        boolean created = group.getStatus() == GroupStatus.CREATED;
        boolean active = group.getStatus() == GroupStatus.ACTIVE;
        activateButton.setVisible(editor && created);
        closeGroupButton.setVisible(editor && active);
    }

    private void hideMutations() {
        hideGridActions(membersDataGrid);
        hideGridActions(coursesDataGrid);
        activateButton.setVisible(false);
        closeGroupButton.setVisible(false);
        moveUpButton.setVisible(false);
        moveDownButton.setVisible(false);
    }

    private void moveCourse(int delta) {
        GroupCourse selected = coursesDataGrid.getSingleSelectedItem();
        if (selected == null) {
            return;
        }
        List<GroupCourse> ordered = SortOrders.sorted(coursesDc.getMutableItems(), GroupCourse::getSortOrder);
        if (!SortOrders.move(ordered, selected, delta)) {
            return;
        }
        SortOrders.normalize(ordered, GroupCourse::setSortOrder);
        replaceCourses(ordered);
        coursesDataGrid.select(selected);
        refreshMoveButtons();
    }

    private void replaceCourses(List<GroupCourse> ordered) {
        rearrangingCourses = true;
        try {
            List<GroupCourse> mutable = coursesDc.getMutableItems();
            mutable.clear();
            mutable.addAll(ordered);
        } finally {
            rearrangingCourses = false;
        }
    }

    private void refreshMoveButtons() {
        GroupCourse selected = coursesDataGrid.getSingleSelectedItem();
        List<GroupCourse> ordered = selected == null
                ? List.of()
                : SortOrders.sorted(new ArrayList<>(coursesDc.getItems()), GroupCourse::getSortOrder);
        int index = selected == null ? -1 : ordered.indexOf(selected);
        boolean editor = access.isAdmin() || access.isTrainer();
        moveUpButton.setEnabled(editor && index > 0);
        moveDownButton.setEnabled(editor && index >= 0 && index < ordered.size() - 1);
    }

    private void hideGridActions(DataGrid<?> grid) {
        for (String id : new String[]{"createAction", "editAction", "removeAction"}) {
            Action action = grid.getAction(id);
            if (action != null) {
                action.setVisible(false);
                action.setEnabled(false);
            }
        }
    }

    private User currentUser() {
        return dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", access.username())
                .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                .one();
    }
}
