package io.uitrainer.platform.view.user;

import com.google.common.base.Strings;
import io.jmix.flowui.component.combobox.JmixComboBox;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.Route;
import io.jmix.core.EntityStates;
import io.jmix.flowui.component.textfield.TypedTextField;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.MessageBundle;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Arrays;
import java.util.Objects;
import java.util.TimeZone;

@Route(value = "users/:id", layout = MainView.class)
@ViewController(id = PlatformViews.USER_DETAIL)
@ViewDescriptor(path = "user-detail-view.xml")
@EditedEntityContainer("userDc")
public class UserDetailView extends StandardDetailView<User> {

    @ViewComponent
    private TypedTextField<String> usernameField;
    @ViewComponent
    private PasswordField passwordField;
    @ViewComponent
    private PasswordField confirmPasswordField;
    @ViewComponent
    private JmixComboBox<String> timeZoneField;
    @ViewComponent
    private MessageBundle messageBundle;
    @Autowired
    private EntityStates entityStates;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Subscribe
    public void onInit(InitEvent event) {
        timeZoneField.setItems(Arrays.stream(TimeZone.getAvailableIDs()).sorted().toList());
        passwordField.setLabel(messageBundle.getMessage("passwordField.label"));
        confirmPasswordField.setLabel(messageBundle.getMessage("confirmPasswordField.label"));
    }

    @Subscribe
    public void onReady(ReadyEvent event) {
        boolean isNew = entityStates.isNew(getEditedEntity());
        usernameField.setReadOnly(!isNew);
        passwordField.setVisible(isNew);
        confirmPasswordField.setVisible(isNew);
    }

    @Subscribe
    public void onValidation(ValidationEvent event) {
        if (!entityStates.isNew(getEditedEntity())) {
            return;
        }
        if (Strings.isNullOrEmpty(passwordField.getValue())) {
            event.getErrors().add(messageBundle.getMessage("passwordRequired"));
        }
        if (!Objects.equals(passwordField.getValue(), confirmPasswordField.getValue())) {
            event.getErrors().add(messageBundle.getMessage("passwordsDoNotMatch"));
        }
    }

    @Subscribe
    public void onBeforeSave(BeforeSaveEvent event) {
        if (entityStates.isNew(getEditedEntity()) && !Strings.isNullOrEmpty(passwordField.getValue())) {
            getEditedEntity().setPassword(passwordEncoder.encode(passwordField.getValue()));
        }
        if (getEditedEntity().getActive() == null) {
            getEditedEntity().setActive(true);
        }
    }
}
