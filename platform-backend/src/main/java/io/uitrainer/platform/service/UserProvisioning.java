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
import org.springframework.lang.Nullable;
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

        for (RoleAssignmentEntity row : assignments(username)) {
            boolean dropResource = RESOURCE.equals(row.getRoleType())
                    && managedResource(row.getRoleCode())
                    && !desired.contains(row.getRoleCode());
            if (dropResource) dataManager.remove(row);
        }
        for (String code : desired) ensure(username, code, RESOURCE);
        syncRowLevels(username);
    }

    /** Row-level роли строго по resource trainer/student. Идемпотентно. */
    public void syncRowLevels(String username) {
        syncRowLevels(username, null);
    }

    void syncRowLevels(String username, @Nullable String extraResourceCode) {
        Set<String> resource = resourceRoles(username);
        if (extraResourceCode != null) resource.add(extraResourceCode);
        if (resource.contains(TrainerRole.CODE)) {
            ensure(username, TrainerOwnDataRole.CODE, ROW_LEVEL);
        } else {
            drop(username, TrainerOwnDataRole.CODE, ROW_LEVEL);
        }
        if (resource.contains(StudentRole.CODE)) {
            ensure(username, StudentPublishedRole.CODE, ROW_LEVEL);
        } else {
            drop(username, StudentPublishedRole.CODE, ROW_LEVEL);
        }
    }

    private boolean managedResource(String code) {
        return BUSINESS_ROLES.contains(code) || UiMinimalRole.CODE.equals(code);
    }

    private List<RoleAssignmentEntity> assignments(String username) {
        return dataManager.load(RoleAssignmentEntity.class)
                .query("select r from sec_RoleAssignmentEntity r where r.username = :u")
                .parameter("u", username)
                .list();
    }

    private void drop(String username, String code, String type) {
        for (RoleAssignmentEntity row : assignments(username)) {
            if (type.equals(row.getRoleType()) && code.equals(row.getRoleCode())) {
                dataManager.remove(row);
            }
        }
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
