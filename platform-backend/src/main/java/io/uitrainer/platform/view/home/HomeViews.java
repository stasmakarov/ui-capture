package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import io.uitrainer.platform.service.CurrentUserAccess;

/**
 * Кабинет после входа. Несколько ролей не переключаются:
 * администратор → директор → преподаватель → проверяющий → ученик.
 */
public final class HomeViews {
    private HomeViews() {
    }

    public static Class<? extends Component> landing(CurrentUserAccess access) {
        if (access.isAdmin()) return AdminHomeView.class;
        if (access.isDirector()) return DirectorHomeView.class;
        if (access.isTrainer()) return TrainerHomeView.class;
        if (access.isReviewer()) return ReviewQueueView.class;
        if (access.isStudent()) return StudentHomeView.class;
        return null;
    }

    public static void openLanding(UI ui, CurrentUserAccess access) {
        Class<? extends Component> home = landing(access);
        if (home == null) ui.navigate("");
        else ui.navigate(home);
    }
}
