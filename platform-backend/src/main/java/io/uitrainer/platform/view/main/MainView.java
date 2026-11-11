package io.uitrainer.platform.view.main;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.DialogWindows;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.kit.action.ActionPerformedEvent;
import io.jmix.flowui.view.DialogWindow;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.jmix.securityflowui.view.changepassword.ChangePasswordView;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.view.home.HomeViews;
import org.springframework.beans.factory.annotation.Autowired;

@Route("")
@ViewController(id = "MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    @Autowired
    private CurrentUserAccess access;
    @Autowired
    private DialogWindows dialogWindows;

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (MainView.class.equals(event.getNavigationTarget())) {
            Class<? extends Component> home = HomeViews.landing(access);
            if (home != null) {
                event.forwardTo(home);
                return;
            }
        }
        super.beforeEnter(event);
    }

    @Subscribe("changePasswordAction")
    public void onChangePassword(ActionPerformedEvent event) {
        DialogWindow<ChangePasswordView> dialog = dialogWindows.view(this, ChangePasswordView.class).build();
        dialog.getView().setUsername(access.username());
        dialog.getView().setCurrentPasswordRequired(true);
        dialog.open();
    }
}
