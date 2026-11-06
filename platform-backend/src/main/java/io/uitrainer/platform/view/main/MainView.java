package io.uitrainer.platform.view.main;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.app.main.StandardMainView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.view.home.HomeViews;
import org.springframework.beans.factory.annotation.Autowired;

@Route("")
@ViewController(id = "MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    @Autowired
    private CurrentUserAccess access;

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
}
