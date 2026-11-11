package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.TabSheet;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteConfiguration;
import com.vaadin.flow.server.streams.UploadHandler;
import io.jmix.core.DataManager;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.api.ApiVersion;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.studio.StudioDownload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** Кабинет платформы: преподаватели и директора, студенты, студия. */
@Route(value = "cabinet/admin", layout = MainView.class)
@ViewController(id = PlatformViews.ADMIN_HOME)
public class AdminHomeView extends StandardView implements BeforeEnterObserver {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private UserProvisioning provisioning;
    @Autowired
    private ViewNavigators viewNavigators;
    @Autowired
    private StudioReleaseService releases;
    @Autowired
    private CurrentUserAccess access;
    @Autowired(required = false)
    @Nullable
    private BuildProperties buildProperties;

    private TabSheet tabs;
    private Tab teachersTab;
    private Tab studentsTab;
    private Tab studioTab;
    private StaffList staff;
    private StudentList students;
    private boolean applyingTab;
    private String pendingTab = "teachers";

    public AdminHomeView() {
        getContent().setWidthFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Кабинет платформы";
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        pendingTab = event.getLocation().getQueryParameters().getSingleParameter("tab").orElse("teachers");
        selectTab(pendingTab);
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Кабинет платформы");
        title.getStyle().set("margin", "0");
        Span lead = new Span("Пользователи кабинета и загрузка студии. Пароль и роли — в карточке пользователя.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");

        staff = new StaffList(dataManager, provisioning, access, viewNavigators, this);
        staff.reload();
        students = new StudentList(dataManager, provisioning, access, viewNavigators, this);
        students.reload();

        tabs = new TabSheet();
        tabs.setWidthFull();
        teachersTab = tabs.add("Преподаватели и директора", staff);
        studentsTab = tabs.add("Студенты", students);
        studioTab = tabs.add("Студия", studioPane());
        tabs.addSelectedChangeListener(e -> {
            if (applyingTab) {
                return;
            }
            writeTab(keyOf(e.getSelectedTab()));
        });
        getContent().add(title, lead, tabs);
        selectTab(pendingTab);
    }

    private void selectTab(String key) {
        if (tabs == null) {
            return;
        }
        applyingTab = true;
        try {
            Tab tab = switch (key) {
                case "students" -> studentsTab;
                case "studio" -> studioTab;
                default -> teachersTab;
            };
            tabs.setSelectedTab(tab);
        } finally {
            applyingTab = false;
        }
    }

    private String keyOf(Tab tab) {
        if (tab == studentsTab) {
            return "students";
        }
        if (tab == studioTab) {
            return "studio";
        }
        return "teachers";
    }

    private void writeTab(String key) {
        getUI().ifPresent(ui -> {
            String path = RouteConfiguration.forSessionScope().getUrl(AdminHomeView.class);
            ui.getPage().getHistory().replaceState(null,
                    path + "?" + new QueryParameters(Map.of("tab", List.of(key))).getQueryString());
        });
    }

    private VerticalLayout studioPane() {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(false);
        box.setSpacing(true);
        H3 heading = new H3("Студия для Windows");
        heading.getStyle().set("margin", "0");
        String platformVersion = buildProperties == null ? "0.6.0" : buildProperties.getVersion();
        box.add(heading, new Span("Платформа " + platformVersion + " · API " + ApiVersion.CURRENT));
        var current = releases.current(StudioReleaseService.WIN);
        if (current.isEmpty()) {
            Button download = new Button("Скачать студию");
            download.setId("downloadStudio");
            download.setEnabled(false);
            box.add(new Span("Релиз студии ещё не загружен"), download);
        } else {
            StudioRelease release = current.get();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            String size = release.getSize() == null ? "" : String.format("%.1f МБ", release.getSize() / (1024.0 * 1024.0));
            String uploaded = release.getUploadedAt() == null ? "" : fmt.format(release.getUploadedAt().toLocalDateTime());
            box.add(new Span("Версия " + release.getVersionName() + " · " + uploaded
                    + " · " + release.getUploadedBy() + " · " + size));
            box.add(StudioDownload.link(release));
        }
        box.add(uploadForm());
        return box;
    }

    private VerticalLayout uploadForm() {
        VerticalLayout form = new VerticalLayout();
        form.setPadding(false);
        form.setSpacing(true);
        form.add(new Span("Загрузить релиз"));
        Span hint = new Span("Артефакт CI uitrainer-studio-windows: файл «uitrainer Studio Setup X.Y.Z.exe» и latest.yml.");
        hint.getStyle().set("color", "var(--lumo-secondary-text-color)");
        form.add(hint);
        Span parsed = new Span();
        parsed.setId("studioParsedVersion");
        AtomicReference<File> exeFile = new AtomicReference<>();
        AtomicReference<String> exeName = new AtomicReference<>();
        Upload exe = new Upload(UploadHandler.toTempFile((metadata, file) -> {
            File previous = exeFile.getAndSet(file);
            if (previous != null) deleteQuietly(previous);
            exeName.set(metadata.fileName());
        }));
        exe.setAcceptedFileTypes(".exe");
        exe.setMaxFiles(1);
        Span exeCaption = new Span("Установщик (.exe)");
        AtomicReference<byte[]> ymlBytes = new AtomicReference<>();
        Upload yml = new Upload(UploadHandler.inMemory((metadata, data) -> {
            ymlBytes.set(data);
            try {
                parsed.setText("Будет загружена версия " + StudioReleaseService.parseLatestYml(data).version());
            } catch (PackageException ex) {
                parsed.setText(ex.getMessage());
            }
        }));
        yml.setAcceptedFileTypes(".yml", ".yaml");
        yml.setMaxFiles(1);
        Span ymlCaption = new Span("Файл обновлений (latest.yml)");
        Button send = new Button("Загрузить релиз", e -> {
            File file = exeFile.get();
            if (file == null) {
                Notification.show("Нужен файл .exe", 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                try (InputStream exeStream = new FileInputStream(file)) {
                    releases.uploadWin(null, exeName.get(), exeStream, ymlBytes.get());
                }
                exeFile.set(null);
                deleteQuietly(file);
                Notification.show("Релиз загружен", 4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.navigate(AdminHomeView.class,
                        QueryParameters.simple(Map.of("tab", "studio"))));
            } catch (ConflictException ex) {
                Notification.show(ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            } catch (PackageException | java.io.IOException ex) {
                Notification.show(ex.getMessage() == null ? "Не удалось загрузить" : ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        send.setId("uploadStudioRelease");
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        form.add(exeCaption, exe, ymlCaption, yml, parsed, send);
        form.addDetachListener(ev -> {
            File left = exeFile.getAndSet(null);
            if (left != null) deleteQuietly(left);
        });
        return form;
    }

    private static void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (java.io.IOException ignored) {
            /* временный файл удалит ОС */
        }
    }
}
