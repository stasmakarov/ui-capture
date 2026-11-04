package io.uicapture.platform.view.token;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uicapture.platform.entity.StudioToken;
import io.uicapture.platform.security.PlatformViews;
import io.uicapture.platform.service.CurrentUserAccess;
import io.uicapture.platform.service.StudioTokenService;
import io.uicapture.platform.view.lesson.StatusBadges;
import io.uicapture.platform.view.main.MainView;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.OffsetDateTime;

/** «Мои токены студии»: выпуск (токен показывается один раз) и отзыв. */
@Route(value = "studio-tokens", layout = MainView.class)
@ViewController(id = PlatformViews.STUDIO_TOKENS)
public class StudioTokenListView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private StudioTokenService tokenService;
    @Autowired
    private CurrentUserAccess access;

    private final Grid<StudioToken> grid = new Grid<>(StudioToken.class, false);

    public StudioTokenListView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
    }

    @Override
    public String getPageTitle() {
        return "Мои токены студии";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        TextField name = new TextField("Название");
        name.setPlaceholder("MacBook тренера");
        name.setId("tokenName");
        ComboBox<Integer> days = new ComboBox<>("Срок, дней");
        days.setItems(30, 90, 365);
        days.setValue(90);
        days.setWidth("8em");
        Button issue = new Button("Выпустить токен", VaadinIcon.PLUS.create(), e -> issue(name.getValue(), days.getValue()));
        issue.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        issue.setId("issueToken");
        HorizontalLayout toolbar = new HorizontalLayout(name, days, issue);
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        grid.addColumn(StudioToken::getName).setHeader("Название").setFlexGrow(2);
        grid.addColumn(t -> t.getPrefix() + "…").setHeader("Начало токена").setFlexGrow(1);
        grid.addColumn(t -> StatusBadges.date(t.getCreatedDate())).setHeader("Выпущен").setFlexGrow(1);
        grid.addColumn(t -> t.getExpiresAt() == null ? "бессрочно" : StatusBadges.date(t.getExpiresAt())).setHeader("Действует до").setFlexGrow(1);
        grid.addColumn(t -> StatusBadges.date(t.getLastUsedAt())).setHeader("Последнее использование").setFlexGrow(1);
        grid.addColumn(this::state).setHeader("Состояние").setFlexGrow(1);
        grid.addComponentColumn(t -> {
            Button revoke = new Button("Отозвать", e -> {
                tokenService.revoke(t.getId());
                Notification.show("Токен отозван: студия с ним больше не сможет публиковать уроки");
                reload();
            });
            revoke.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);
            revoke.setEnabled(!Boolean.TRUE.equals(t.getRevoked()));
            return revoke;
        }).setFlexGrow(0).setWidth("9em");
        grid.setSizeFull();
        grid.setId("tokensGrid");

        getContent().add(new Paragraph("Токен связывает студию на вашем компьютере с платформой. "
                + "Обычно он выпускается сам, когда вы нажимаете «Записать в студии». Здесь можно выпустить токен вручную "
                + "(npx ui-capture platform-login) и отозвать потерянный."), toolbar, grid);
        reload();
    }

    private String state(StudioToken token) {
        if (Boolean.TRUE.equals(token.getRevoked())) return "отозван";
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now())) return "истёк";
        return "действует";
    }

    private void issue(String name, Integer days) {
        StudioTokenService.IssuedToken issued = tokenService.issueToken(name, Duration.ofDays(days == null ? 90 : days));
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Новый токен студии");
        TextField value = new TextField();
        value.setValue(issued.plainText());
        value.setReadOnly(true);
        value.setWidthFull();
        value.addClassName("uic-token-plain");
        value.setId("issuedTokenValue");
        dialog.add(new Paragraph("Скопируйте токен сейчас — он показывается один раз, на сервере хранится только его хэш."), value);
        Button close = new Button("Готово", e -> dialog.close());
        close.setId("closeTokenDialog");
        dialog.getFooter().add(close);
        dialog.setWidth("44em");
        dialog.open();
        reload();
    }

    private void reload() {
        grid.setItems(dataManager.load(StudioToken.class)
                .query("select t from uic_StudioToken t where t.owner.username = :u order by t.createdDate desc")
                .parameter("u", access.username()).list());
    }
}
