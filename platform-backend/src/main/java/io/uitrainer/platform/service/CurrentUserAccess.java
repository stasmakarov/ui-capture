package io.uitrainer.platform.service;

import io.jmix.core.security.CurrentAuthentication;
import io.uitrainer.platform.security.AdminRole;
import io.uitrainer.platform.security.DirectorRole;
import io.uitrainer.platform.security.ReviewerRole;
import io.uitrainer.platform.security.StudentRole;
import io.uitrainer.platform.security.TrainerRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

/** Проверка ролей текущего пользователя для бизнес-правил (ЖЦ, импорт). */
@Component
public class CurrentUserAccess {

    private final CurrentAuthentication currentAuthentication;

    public CurrentUserAccess(CurrentAuthentication currentAuthentication) {
        this.currentAuthentication = currentAuthentication;
    }

    public String username() {
        return currentAuthentication.getUser().getUsername();
    }

    public boolean hasRole(String code) {
        for (GrantedAuthority authority : currentAuthentication.getAuthentication().getAuthorities()) {
            String name = authority.getAuthority();
            if (name == null) continue;
            if (name.equals(code) || name.equals("ROLE_" + code)) return true;
        }
        return false;
    }

    public boolean isAdmin() {
        return hasRole(AdminRole.CODE) || "system".equals(username());
    }

    public boolean isReviewer() {
        return hasRole(ReviewerRole.CODE);
    }

    public boolean isTrainer() {
        return hasRole(TrainerRole.CODE);
    }

    public boolean isDirector() {
        return hasRole(DirectorRole.CODE);
    }

    public boolean isStudent() {
        return hasRole(StudentRole.CODE);
    }
}
