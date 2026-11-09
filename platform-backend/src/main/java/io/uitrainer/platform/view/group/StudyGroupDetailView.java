package io.uitrainer.platform.view.group;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.InstanceContainer;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
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
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

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
    @Autowired
    private GroupProgressService groupProgress;

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
    private InstanceContainer<StudyGroup> studyGroupDc;
    @ViewComponent
    private JmixButton moveUpButton;
    @ViewComponent
    private JmixButton moveDownButton;

    @Subscribe
    public void onInit(InitEvent event) {
        activateButton.addClickListener(e -> changeStatus(true));
        closeGroupButton.addClickListener(e -> changeStatus(false));
        moveUpButton.addClickListener(e -> moveCourse(-1));
        moveDownButton.addClickListener(e -> moveCourse(1));
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
        StudyGroup group = getEditedEntity();
        if (selected == null || entityStates.isNew(group)) {
            Notification.show("Сначала сохраните группу и выберите курс", 4000, Notification.Position.MIDDLE);
            return;
        }
        groupProgress.moveCourse(group, selected, delta);
        getUI().ifPresent(ui -> ui.getPage().reload());
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
