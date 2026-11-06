package io.uitrainer.platform.view.lesson;

import io.jmix.core.DataManager;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.combobox.EntityComboBox;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.CurrentUserAccess;
import org.springframework.beans.factory.annotation.Autowired;

/** Диалог урока внутри курса. Карточка ЖЦ остаётся на {@code uic_Lesson.detail}. */
@ViewController(id = PlatformViews.LESSON_EDIT)
@ViewDescriptor(path = "lesson-edit-view.xml")
@EditedEntityContainer("lessonDc")
public class LessonEditView extends StandardDetailView<Lesson> {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private CurrentUserAccess access;

    @ViewComponent
    private TypedTextField<String> externalIdField;
    @ViewComponent
    private EntityComboBox<User> authorField;

    @Subscribe
    public void onInitEntity(InitEntityEvent<Lesson> event) {
        Lesson lesson = event.getEntity();
        lesson.setStatus(LessonStatus.DRAFT);
        lesson.setAuthor(currentUser());
    }

    @Subscribe
    public void onBeforeShow(BeforeShowEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            externalIdField.setReadOnly(true);
        }
        if (!access.isAdmin()) {
            authorField.setReadOnly(true);
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
