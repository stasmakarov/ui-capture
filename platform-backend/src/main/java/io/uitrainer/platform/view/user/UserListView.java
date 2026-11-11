package io.uitrainer.platform.view.user;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.LookupComponent;
import io.jmix.flowui.view.StandardListView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;

@Route(value = "users", layout = MainView.class)
@ViewController(id = PlatformViews.USER_LIST)
@ViewDescriptor(path = "user-list-view.xml")
@LookupComponent("usersDataGrid")
public class UserListView extends StandardListView<User> {
}
