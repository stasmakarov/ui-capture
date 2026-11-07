package io.uitrainer.platform.app;

import io.jmix.core.DataManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.LifecycleTransition;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Демо-пользователи, демо-курс и уроки для кабинетов в профилях dev и test.
 * Пароль совпадает с логином. Учётная запись admin уже есть в Liquibase — здесь она не дублируется.
 * Вход: admin/admin, trainer/trainer, trainer2/trainer2, reviewer/reviewer, student/student, director/director.
 */
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
            user("director", "Директор", "Смирнова", List.of(DirectorRole.CODE, UiMinimalRole.CODE), List.of());
            boolean hasCourse = dataManager.load(Course.class).query("select c from uic_Course c where c.code = :c")
                    .parameter("c", DEMO_COURSE_CODE).optional().isPresent();
            if (!hasCourse) {
                Course course = dataManager.create(Course.class);
                course.setCode(DEMO_COURSE_CODE);
                course.setTitle("Демо-курс: работа на zakupki.gov.ru");
                course.setDescription("Демо-курс: уроки, записанные кнопкой «Записать в студии», привязываются к нему.");
                course.setOwner(trainer);
                dataManager.save(course);
            }
            Course course = dataManager.load(Course.class).query("select c from uic_Course c where c.code = :c")
                    .parameter("c", DEMO_COURSE_CODE).one();
            seedCabinetLessons(trainer, course);
            log.info("Демо-вход (пароль совпадает с логином): admin, trainer, trainer2, reviewer, student, director");
        });
    }

    /**
     * Строки уроков без пакета студии, чтобы счётчики и очередь проверки были не пустыми.
     * Предпросмотра файла у них нет.
     */
    private void seedCabinetLessons(User trainer, Course course) {
        OffsetDateTime now = OffsetDateTime.now();
        createLessonIfAbsent(trainer, course, "demo-draft", "Демо: черновик урока", LessonStatus.DRAFT);
        createLessonIfAbsent(trainer, course, "demo-published", "Демо: опубликованный урок", LessonStatus.PUBLISHED)
                .ifPresent(lesson -> journal(lesson, LessonStatus.REVIEW, LessonStatus.PUBLISHED, trainer.getUsername(), null, now));
        createLessonIfAbsent(trainer, course, "demo-review", "Демо: урок на проверке", LessonStatus.REVIEW)
                .ifPresent(lesson -> journal(lesson, LessonStatus.DRAFT, LessonStatus.REVIEW, trainer.getUsername(), null, now.minusMinutes(20)));
        createLessonIfAbsent(trainer, course, "demo-returned", "Демо: возвращённый урок", LessonStatus.DRAFT)
                .ifPresent(lesson -> {
                    journal(lesson, LessonStatus.DRAFT, LessonStatus.REVIEW, trainer.getUsername(), null, now.minusMinutes(10));
                    journal(lesson, LessonStatus.REVIEW, LessonStatus.DRAFT, trainer.getUsername(),
                            "Демо: поправьте формулировку шага", now);
                });
    }

    private Optional<Lesson> createLessonIfAbsent(User author, Course course, String externalId, String title, LessonStatus status) {
        boolean exists = dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.externalId = :id")
                .parameter("id", externalId)
                .optional()
                .isPresent();
        if (exists) return Optional.empty();
        Lesson lesson = dataManager.create(Lesson.class);
        lesson.setExternalId(externalId);
        lesson.setTitle(title);
        lesson.setAuthor(author);
        lesson.setCourse(course);
        lesson.setStatus(status);
        lesson.setSourceSite("zakupki.gov.ru");
        return Optional.of(dataManager.save(lesson));
    }

    private void journal(Lesson lesson, LessonStatus from, LessonStatus to, String actor, String comment, OffsetDateTime at) {
        LifecycleTransition transition = dataManager.create(LifecycleTransition.class);
        transition.setLesson(lesson);
        transition.setFromStatus(from);
        transition.setToStatus(to);
        transition.setActor(actor);
        transition.setAt(at);
        transition.setComment(comment);
        dataManager.save(transition);
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
