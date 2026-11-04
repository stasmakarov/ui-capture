package io.uicapture.platform.app;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uicapture.platform.entity.Course;
import io.uicapture.platform.entity.User;
import io.uicapture.platform.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/** Демо-пользователи и демо-курс в профилях dev и test. Пароль совпадает с логином. */
@Component
@Profile({"dev", "test"})
public class DemoDataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);
    public static final String DEMO_COURSE_CODE = "demo";

    private final DataManager dataManager;
    private final SystemAuthenticator systemAuthenticator;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(DataManager dataManager, SystemAuthenticator systemAuthenticator, PasswordEncoder passwordEncoder) {
        this.dataManager = dataManager;
        this.systemAuthenticator = systemAuthenticator;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        systemAuthenticator.runWithSystem(() -> {
            user("admin", "Админ", "Платформы", List.of(AdminRole.CODE), List.of());
            user("reviewer", "Ревьюер", "Проверяющий", List.of(ReviewerRole.CODE, UiMinimalRole.CODE), List.of());
            User trainer = user("trainer", "Тренер", "Иванова", List.of(TrainerRole.CODE, UiMinimalRole.CODE), List.of(TrainerOwnDataRole.CODE));
            user("trainer2", "Тренер", "Петров", List.of(TrainerRole.CODE, UiMinimalRole.CODE), List.of(TrainerOwnDataRole.CODE));
            user("student", "Ученик", "Сидоров", List.of(StudentRole.CODE, UiMinimalRole.CODE), List.of(StudentPublishedRole.CODE));
            boolean hasCourse = dataManager.load(Course.class).query("select c from uic_Course c where c.code = :c")
                    .parameter("c", DEMO_COURSE_CODE).optional().isPresent();
            if (!hasCourse) {
                Course course = dataManager.create(Course.class);
                course.setCode(DEMO_COURSE_CODE);
                course.setTitle("Демо-курс: работа на zakupki.gov.ru");
                course.setDescription("Заглушка курса до M3: уроки, записанные кнопкой «Записать в студии», привязываются к нему.");
                course.setOwner(trainer);
                dataManager.save(course);
            }
            log.info("Демо-данные готовы: admin/admin, reviewer/reviewer, trainer/trainer, trainer2/trainer2, student/student");
        });
    }

    private User user(String username, String firstName, String lastName, List<String> resourceRoles, List<String> rowRoles) {
        User user = dataManager.load(User.class).query("select u from User u where u.username = :u")
                .parameter("u", username).optional().orElse(null);
        if (user == null) {
            user = dataManager.create(User.class);
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(username));
            user.setActive(true);
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user = dataManager.save(user);
        for (String role : resourceRoles) assign(username, role, "resource");
        for (String role : rowRoles) assign(username, role, "row_level");
        return user;
    }

    private void assign(String username, String role, String type) {
        boolean exists = dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :r")
                .parameter("u", username).parameter("r", role).optional().isPresent();
        if (exists) return;
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(username);
        assignment.setRoleCode(role);
        assignment.setRoleType(type);
        dataManager.save(assignment);
    }
}
