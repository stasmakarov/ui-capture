package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.securitydata.entity.RoleAssignmentEntity;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.AdminRole;
import io.uitrainer.platform.security.DirectorRole;
import io.uitrainer.platform.security.ReviewerRole;
import io.uitrainer.platform.security.StudentPublishedRole;
import io.uitrainer.platform.security.StudentRole;
import io.uitrainer.platform.security.TrainerOwnDataRole;
import io.uitrainer.platform.security.TrainerRole;
import io.uitrainer.platform.security.UiMinimalRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Пользователи и назначения ролей в тех же таблицах, что и демо-сид. Отдельного справочника проверяющих нет. */
@Service
public class UserProvisioning {

    public static final String RESOURCE = "resource";
    public static final String ROW_LEVEL = "row_level";

    private static final List<String> BUSINESS_ROLES = List.of(
            AdminRole.CODE, DirectorRole.CODE, TrainerRole.CODE, ReviewerRole.CODE, StudentRole.CODE);

    private final DataManager dataManager;
    private final PasswordEncoder passwordEncoder;

    public UserProvisioning(DataManager dataManager, PasswordEncoder passwordEncoder) {
        this.dataManager = dataManager;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void createTeacher(String username, String firstName, String lastName, String password) {
        createUser(username, firstName, lastName, password, TrainerRole.CODE);
    }

    @Transactional
    public User createUser(String username, String firstName, String lastName, String password, String role) {
        String login = username == null ? "" : username.trim();
        if (login.isEmpty() || login.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("Укажите логин без пробелов");
        }
        if (firstName == null || firstName.isBlank() || lastName == null || lastName.isBlank()) {
            throw new IllegalArgumentException("Укажите имя и фамилию");
        }
        if (!BUSINESS_ROLES.contains(role)) {
            throw new IllegalArgumentException("Неизвестная роль");
        }
        boolean exists = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", login)
                .optional()
                .isPresent();
        if (exists) throw new IllegalArgumentException("Пользователь с таким логином уже есть");
        String raw = password == null || password.isBlank() ? login : password;
        User user = dataManager.create(User.class);
        user.setUsername(login);
        user.setFirstName(firstName.trim());
        user.setLastName(lastName.trim());
        user.setPassword(passwordEncoder.encode(raw));
        user.setActive(true);
        dataManager.save(user);
        saveResourceRoles(login, Set.of(role));
        return user;
    }

    @Transactional
    public void setActive(String username, boolean active) {
        User user = dataManager.load(User.class)
                .query("select u from User u where u.username = :u")
                .parameter("u", username)
                .one();
        user.setActive(active);
        dataManager.save(user);
    }

    public Set<String> resourceRoles(String username) {
        Set<String> codes = new HashSet<>();
        for (RoleAssignmentEntity row : assignments(username)) {
            if (RESOURCE.equals(row.getRoleType())) codes.add(row.getRoleCode());
        }
        return codes;
    }

    @Transactional
    public void saveResourceRoles(String username, Set<String> selected) {
        Set<String> desired = new HashSet<>();
        if (selected != null) {
            for (String code : selected) {
                if (BUSINESS_ROLES.contains(code)) desired.add(code);
            }
        }
        desired.add(UiMinimalRole.CODE);
        Set<String> desiredRows = new HashSet<>();
        if (desired.contains(TrainerRole.CODE)) desiredRows.add(TrainerOwnDataRole.CODE);
        if (desired.contains(StudentRole.CODE)) desiredRows.add(StudentPublishedRole.CODE);

        for (RoleAssignmentEntity row : assignments(username)) {
            boolean dropResource = RESOURCE.equals(row.getRoleType())
                    && managedResource(row.getRoleCode())
                    && !desired.contains(row.getRoleCode());
            boolean dropRow = ROW_LEVEL.equals(row.getRoleType())
                    && managedRow(row.getRoleCode())
                    && !desiredRows.contains(row.getRoleCode());
            if (dropResource || dropRow) dataManager.remove(row);
        }
        for (String code : desired) ensure(username, code, RESOURCE);
        for (String code : desiredRows) ensure(username, code, ROW_LEVEL);
    }

    private boolean managedResource(String code) {
        return BUSINESS_ROLES.contains(code) || UiMinimalRole.CODE.equals(code);
    }

    private boolean managedRow(String code) {
        return TrainerOwnDataRole.CODE.equals(code) || StudentPublishedRole.CODE.equals(code);
    }

    private List<RoleAssignmentEntity> assignments(String username) {
        return dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u")
                .parameter("u", username)
                .list();
    }

    private void ensure(String username, String code, String type) {
        boolean exists = dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u and r.roleCode = :c and r.roleType = :t")
                .parameter("u", username)
                .parameter("c", code)
                .parameter("t", type)
                .optional()
                .isPresent();
        if (exists) return;
        RoleAssignmentEntity assignment = dataManager.create(RoleAssignmentEntity.class);
        assignment.setUsername(username);
        assignment.setRoleCode(code);
        assignment.setRoleType(type);
        dataManager.save(assignment);
    }
}
