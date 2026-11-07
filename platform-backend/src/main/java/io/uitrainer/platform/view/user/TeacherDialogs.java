package io.uitrainer.platform.view.user;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import io.uitrainer.platform.security.AdminRole;
import io.uitrainer.platform.security.DirectorRole;
import io.uitrainer.platform.security.ReviewerRole;
import io.uitrainer.platform.security.StudentRole;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.UserProvisioning;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Диалоги администратора: создать преподавателя и назначить роли на существующего пользователя. */
public final class TeacherDialogs {
    private TeacherDialogs() {
    }

    public static void createTeacher(UserProvisioning provisioning, Runnable onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Создать преподавателя");
        dialog.setWidth("32em");
        TextField username = new TextField("Логин");
        username.setId("teacherUsername");
        username.setWidthFull();
        TextField firstName = new TextField("Имя");
        firstName.setId("teacherFirstName");
        firstName.setWidthFull();
        TextField lastName = new TextField("Фамилия");
        lastName.setId("teacherLastName");
        lastName.setWidthFull();
        PasswordField password = new PasswordField("Пароль");
        password.setId("teacherPassword");
        password.setWidthFull();
        password.setHelperText("Если пусто, пароль совпадёт с логином");
        dialog.add(username, firstName, lastName, password);
        Button save = new Button("Создать", e -> {
            try {
                boolean generated = password.getValue() == null || password.getValue().isBlank();
                provisioning.createTeacher(username.getValue(), firstName.getValue(), lastName.getValue(), password.getValue());
                dialog.close();
                Notification.show(generated
                                ? "Преподаватель создан. Пароль совпадает с логином."
                                : "Преподаватель создан.",
                        4000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                onSaved.run();
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage() == null ? "Не удалось создать преподавателя" : ex.getMessage(),
                        5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.setId("teacherSave");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(save, new Button("Отмена", e -> dialog.close()));
        dialog.open();
    }

    public static void assignRoles(UserProvisioning provisioning, CurrentUserAccess access,
                                    String username, String name, Runnable onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Роли: " + name);
        dialog.setWidth("28em");
        CheckboxGroup<String> roles = new CheckboxGroup<>();
        roles.setId("roleGroup");
        roles.setLabel("Роли пользователя");
        List<String> items = List.of(AdminRole.CODE, DirectorRole.CODE, TrainerRole.CODE, ReviewerRole.CODE, StudentRole.CODE);
        roles.setItems(items);
        roles.setItemLabelGenerator(TeacherDialogs::label);
        Set<String> current = new LinkedHashSet<>(provisioning.resourceRoles(username));
        current.retainAll(items);
        roles.setValue(current);
        dialog.add(roles);
        Button save = new Button("Сохранить", e -> {
            Set<String> selected = roles.getValue() == null ? Set.of() : roles.getValue();
            if (username.equals(access.username()) && !selected.contains(AdminRole.CODE)) {
                Notification.show("Нельзя снять с себя роль администратора", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                provisioning.saveResourceRoles(username, selected);
                dialog.close();
                Notification.show("Роли сохранены", 3000, Notification.Position.BOTTOM_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                onSaved.run();
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage() == null ? "Не удалось сохранить роли" : ex.getMessage(),
                        5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.setId("rolesSave");
        HorizontalLayout footer = new HorizontalLayout(save, new Button("Отмена", e -> dialog.close()));
        footer.setAlignItems(FlexComponent.Alignment.END);
        dialog.getFooter().add(footer);
        dialog.open();
    }

    public static String label(String code) {
        return switch (code) {
            case AdminRole.CODE -> "Администратор";
            case DirectorRole.CODE -> "Директор";
            case TrainerRole.CODE -> "Преподаватель";
            case ReviewerRole.CODE -> "Проверяющий";
            case StudentRole.CODE -> "Ученик";
            case "ui-minimal" -> "Вход в интерфейс";
            case "trainer-own-data" -> "Только свои курсы";
            case "student-published" -> "Только опубликованное";
            default -> code;
        };
    }
}
