package io.uitrainer.platform.view.user;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import io.uitrainer.platform.security.DirectorRole;
import io.uitrainer.platform.security.StudentRole;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.service.UserProvisioning;

/** Диалоги кабинета: создать преподавателя, директора или студента. */
public final class TeacherDialogs {
    private TeacherDialogs() {
    }

    public static void createStaff(UserProvisioning provisioning, Runnable onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Создать пользователя");
        dialog.setWidth("32em");
        ComboBox<String> role = new ComboBox<>("Роль");
        role.setId("staffRole");
        role.setItems(TrainerRole.CODE, DirectorRole.CODE);
        role.setItemLabelGenerator(TeacherDialogs::label);
        role.setValue(TrainerRole.CODE);
        role.setWidthFull();
        openCreate(dialog, provisioning, role, onSaved, "Пользователь создан");
    }

    public static void createStudent(UserProvisioning provisioning, Runnable onSaved) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Создать студента");
        dialog.setWidth("32em");
        ComboBox<String> role = new ComboBox<>("Роль");
        role.setItems(StudentRole.CODE);
        role.setItemLabelGenerator(TeacherDialogs::label);
        role.setValue(StudentRole.CODE);
        role.setVisible(false);
        openCreate(dialog, provisioning, role, onSaved, "Студент создан");
    }

    public static void createTeacher(UserProvisioning provisioning, Runnable onSaved) {
        createStaff(provisioning, onSaved);
    }

    private static void openCreate(Dialog dialog, UserProvisioning provisioning, ComboBox<String> role,
                                   Runnable onSaved, String okText) {
        TextField username = new TextField("Логин");
        username.setId("userUsername");
        username.setWidthFull();
        TextField firstName = new TextField("Имя");
        firstName.setId("userFirstName");
        firstName.setWidthFull();
        TextField lastName = new TextField("Фамилия");
        lastName.setId("userLastName");
        lastName.setWidthFull();
        PasswordField password = new PasswordField("Пароль");
        password.setId("userPassword");
        password.setWidthFull();
        password.setHelperText("Если пусто, пароль совпадёт с логином");
        dialog.add(role, username, firstName, lastName, password);
        Button save = new Button("Создать", e -> {
            try {
                boolean generated = password.getValue() == null || password.getValue().isBlank();
                provisioning.createUser(username.getValue(), firstName.getValue(), lastName.getValue(),
                        password.getValue(), role.getValue());
                dialog.close();
                Notification.show(generated ? okText + ". Пароль совпадает с логином." : okText + ".",
                        4000, Notification.Position.BOTTOM_END).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                onSaved.run();
            } catch (RuntimeException ex) {
                Notification.show(ex.getMessage() == null ? "Не удалось создать" : ex.getMessage(),
                        5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.setId("userSave");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.getFooter().add(save, new Button("Отмена", e -> dialog.close()));
        dialog.open();
    }

    public static String label(String code) {
        return switch (code) {
            case "admin" -> "Администратор";
            case DirectorRole.CODE -> "Директор";
            case TrainerRole.CODE -> "Преподаватель";
            case "reviewer" -> "Проверяющий";
            case StudentRole.CODE -> "Ученик";
            case "ui-minimal" -> "Вход в интерфейс";
            case "trainer-own-data" -> "Только свои курсы";
            case "student-published" -> "Только опубликованное";
            default -> code;
        };
    }
}
