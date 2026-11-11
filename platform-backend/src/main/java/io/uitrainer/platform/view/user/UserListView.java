package io.uitrainer.platform.view.user;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;

@PrimaryListView(User.class)
@Route(value = "users", layout = MainView.class)
@ViewController(id = PlatformViews.USER_LIST)
@ViewDescriptor(path = "user-list-view.xml")
@LookupComponent("usersDataGrid")
public class UserListView extends StandardListView<User> {
}
