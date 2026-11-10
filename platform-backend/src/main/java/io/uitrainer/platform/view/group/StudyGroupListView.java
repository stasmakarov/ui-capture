package io.uitrainer.platform.view.group;

import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.checkbox.JmixCheckbox;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.kit.component.button.JmixButton;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.GroupStatus;
import io.uitrainer.platform.entity.StudyGroup;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.GroupProgressService;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

@Route(value = "groups", layout = MainView.class)
@ViewController(id = PlatformViews.GROUP_LIST)
@ViewDescriptor(path = "study-group-list-view.xml")
@LookupComponent("groupsDataGrid")
public class StudyGroupListView extends StandardListView<StudyGroup> {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private GroupProgressService groupProgress;

    @ViewComponent
    private CollectionLoader<StudyGroup> groupsDl;
    @ViewComponent
    private CollectionContainer<User> trainersDc;
    @ViewComponent
    private DataGrid<StudyGroup> groupsDataGrid;
    @ViewComponent
    private TypedTextField<String> nameField;
    @ViewComponent
    private EntityComboBox<User> ownerField;
    @ViewComponent
    private JmixCheckbox includeClosedField;
    @ViewComponent
    private JmixButton createButton;
    @ViewComponent
    private JmixButton editButton;
    @ViewComponent
    private MessageBundle messageBundle;

    @Subscribe
    public void onInit(InitEvent event) {
        groupsDl.setParameter("closed", GroupStatus.CLOSED.getId());
        boolean editor = access.isTrainer() || access.isAdmin();
        createButton.setVisible(editor);
        Action create = groupsDataGrid.getAction("createAction");
        if (create != null) {
            create.setVisible(editor);
        }
        editButton.setText("Открыть");
        boolean ownerFilter = access.isAdmin() || access.isDirector();
        ownerField.setVisible(ownerFilter);
        if (ownerFilter) {
            trainersDc.setItems(dataManager.unconstrained().load(User.class)
                    .query("select u from User u where u.username in "
                            + "(select r.username from sec_RoleAssignmentEntity r where r.roleCode = :c and r.roleType = 'resource') "
                            + "order by u.username")
                    .parameter("c", TrainerRole.CODE)
                    .fetchPlan(fp -> fp.add("username").add("firstName").add("lastName"))
                    .list());
        }
        groupsDataGrid.addColumn(this::ownerLabel)
                .setHeader(messageBundle.getMessage("ownerFilter"))
                .setKey("ownerLabel");
        groupsDataGrid.addColumn(this::membersCount)
                .setHeader(messageBundle.getMessage("membersCount"))
                .setKey("membersCount");
        groupsDataGrid.addColumn(this::coursesCount)
                .setHeader(messageBundle.getMessage("coursesCount"))
                .setKey("coursesCount");
        groupsDataGrid.addColumn(this::progressLabel)
                .setHeader(messageBundle.getMessage("progress"))
                .setKey("progress");
        groupsDataGrid.setColumnPosition(groupsDataGrid.getColumnByKey("ownerLabel"), 1);
        nameField.addValueChangeListener(e -> applyFilter());
        includeClosedField.addValueChangeListener(e -> applyFilter());
        ownerField.addValueChangeListener(e -> applyFilter());
    }

    private void applyFilter() {
        StringBuilder jpql = new StringBuilder("select e from uic_StudyGroup e where 1=1");
        boolean hideClosed = !Boolean.TRUE.equals(includeClosedField.getValue());
        if (hideClosed) {
            jpql.append(" and e.status <> :closed");
        }
        String name = nameField.getTypedValue();
        if (name == null || name.isBlank()) {
            name = nameField.getValue();
        }
        boolean hasName = name != null && !name.isBlank();
        if (hasName) {
            jpql.append(" and lower(e.name) like :name");
        }
        User owner = ownerField.isVisible() ? ownerField.getValue() : null;
        if (owner != null) {
            jpql.append(" and e.owner = :owner");
        }
        jpql.append(" order by e.name");
        groupsDl.setQuery(jpql.toString());
        if (hideClosed) {
            groupsDl.setParameter("closed", GroupStatus.CLOSED.getId());
        } else {
            groupsDl.removeParameter("closed");
        }
        if (hasName) {
            groupsDl.setParameter("name", "%" + name.trim().toLowerCase() + "%");
        } else {
            groupsDl.removeParameter("name");
        }
        if (owner != null) {
            groupsDl.setParameter("owner", owner);
        } else {
            groupsDl.removeParameter("owner");
        }
        groupsDl.load();
    }

    private String ownerLabel(StudyGroup group) {
        User owner = group.getOwner();
        if (owner == null) {
            return "";
        }
        String display = owner.getDisplayName();
        return display == null || display.isBlank() ? owner.getUsername() : display;
    }

    private long membersCount(StudyGroup group) {
        return group.getMembers() == null ? 0 : group.getMembers().size();
    }

    private long coursesCount(StudyGroup group) {
        return group.getCourses() == null ? 0 : group.getCourses().size();
    }

    private String progressLabel(StudyGroup group) {
        return groupProgress.summarize(group).progressLabel();
    }
}
