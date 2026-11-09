package io.uitrainer.platform.view.group;

import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.DialogMode;
import io.jmix.flowui.view.EditedEntityContainer;
import io.jmix.flowui.view.StandardDetailView;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.view.main.MainView;

@Route(value = "group-members/:id", layout = MainView.class)
@ViewController(id = PlatformViews.GROUP_MEMBER_DETAIL)
@ViewDescriptor(path = "group-member-detail-view.xml")
@EditedEntityContainer("groupMemberDc")
@DialogMode(width = "32em")
public class GroupMemberDetailView extends StandardDetailView<GroupMember> {
}
