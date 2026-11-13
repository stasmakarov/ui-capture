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
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;
import io.uitrainer.platform.service.StudioUploadState;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.studio.StudioLocalUrl;
import io.uitrainer.platform.view.studio.StudioPresence;
import org.springframework.beans.factory.annotation.Autowired;

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

    private static final DateTimeFormatter UPLOADED_AT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
    @Autowired
    private StudioLocalUrl studioLocalUrl;

    private TabSheet tabs;
    private Tab teachersTab;
    private Tab studentsTab;
    private Tab studioTab;
    private StaffList staff;
    private StudentList students;
    private boolean applyingTab;
    private String pendingTab = "teachers";

    private Span serverTitle;
    private Span serverMeta;
    private VerticalLayout presenceHolder;
    private final AtomicReference<File> exeFile = new AtomicReference<>();
    private final AtomicReference<String> exeName = new AtomicReference<>();
    private final AtomicReference<byte[]> ymlBytes = new AtomicReference<>();
    private Upload exeUpload;
    private Upload ymlUpload;
    private Span uploadHint;
    private Span uploadError;
    private Button uploadButton;

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
        serverTitle = new Span();
        serverTitle.setId("studioServerTitle");
        serverMeta = new Span();
        serverMeta.setId("studioServerMeta");
        serverMeta.getStyle().set("color", "var(--lumo-secondary-text-color)");
        H3 localHeading = new H3("Студия на этом компьютере");
        localHeading.getStyle().set("margin", "0");
        presenceHolder = new VerticalLayout();
        presenceHolder.setPadding(false);
        presenceHolder.setSpacing(false);
        box.add(serverTitle, serverMeta, uploadForm(), localHeading, presenceHolder);
        refreshStudioHeader();
        refreshUploadState();
        return box;
    }

    private void refreshStudioHeader() {
        var current = releases.current(StudioReleaseService.WIN);
        if (current.isEmpty()) {
            serverTitle.setText("Релиз студии ещё не загружен");
            serverMeta.setText("");
            serverMeta.setVisible(false);
        } else {
            StudioRelease release = current.get();
            serverTitle.setText("На сервере: Студия " + release.getVersionName() + " для Windows");
            String size = release.getSize() == null ? "" : String.format("%.1f МБ", release.getSize() / (1024.0 * 1024.0));
            String uploaded = release.getUploadedAt() == null ? "" : UPLOADED_AT.format(release.getUploadedAt().toLocalDateTime());
            serverMeta.setText("Загружена " + uploaded + " · " + release.getUploadedBy() + " · " + size);
            serverMeta.setVisible(true);
        }
        presenceHolder.removeAll();
        presenceHolder.add(new StudioPresence(current.orElse(null), studioLocalUrl.resolve()));
    }

    private VerticalLayout uploadForm() {
        VerticalLayout form = new VerticalLayout();
        form.setPadding(false);
        form.setSpacing(true);
        H3 heading = new H3("Загрузить релиз Студии");
        heading.getStyle().set("margin", "0");
        exeUpload = new Upload(UploadHandler.toTempFile((metadata, file) -> {
            File previous = exeFile.getAndSet(file);
            if (previous != null) deleteQuietly(previous);
            exeName.set(metadata.fileName());
        }));
        exeUpload.setAcceptedFileTypes(".exe");
        exeUpload.setMaxFiles(1);
        exeUpload.addSucceededListener(e -> refreshUploadState());
        exeUpload.addFileRemovedListener(e -> {
            File left = exeFile.getAndSet(null);
            if (left != null) deleteQuietly(left);
            exeName.set(null);
            refreshUploadState();
        });
        Span exeCaption = new Span("Установщик (.exe)");
        ymlUpload = new Upload(UploadHandler.inMemory((metadata, data) -> ymlBytes.set(data)));
        ymlUpload.setAcceptedFileTypes(".yml", ".yaml");
        ymlUpload.setMaxFiles(1);
        ymlUpload.addSucceededListener(e -> refreshUploadState());
        ymlUpload.addFileRemovedListener(e -> {
            ymlBytes.set(null);
            refreshUploadState();
        });
        Span ymlCaption = new Span("Файл обновлений (latest.yml)");
        uploadHint = new Span();
        uploadHint.setId("studioParsedVersion");
        uploadHint.getStyle().set("color", "var(--lumo-secondary-text-color)");
        uploadError = new Span();
        uploadError.setId("studioUploadError");
        uploadError.getStyle().set("color", "var(--lumo-error-text-color)");
        uploadButton = new Button("Загрузить релиз", e -> uploadRelease());
        uploadButton.setId("uploadStudioRelease");
        uploadButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        form.add(heading, exeCaption, exeUpload, ymlCaption, ymlUpload, uploadHint, uploadError, uploadButton);
        form.addDetachListener(ev -> {
            File left = exeFile.getAndSet(null);
            if (left != null) deleteQuietly(left);
        });
        return form;
    }

    private void refreshUploadState() {
        String server = releases.current(StudioReleaseService.WIN)
                .map(StudioRelease::getVersionName)
                .orElse(null);
        StudioUploadState.Decision decision = StudioUploadState.of(exeFile.get() != null, ymlBytes.get(), server);
        uploadButton.setEnabled(decision.enabled());
        uploadHint.setText(decision.reason());
    }

    private void uploadRelease() {
        uploadError.setText("");
        File file = exeFile.get();
        byte[] yml = ymlBytes.get();
        String server = releases.current(StudioReleaseService.WIN)
                .map(StudioRelease::getVersionName)
                .orElse(null);
        StudioUploadState.Decision decision = StudioUploadState.of(file != null, yml, server);
        if (!decision.enabled()) {
            uploadHint.setText(decision.reason());
            uploadButton.setEnabled(false);
            return;
        }
        try {
            try (InputStream exeStream = new FileInputStream(file)) {
                releases.uploadWin(null, exeName.get(), exeStream, yml);
            }
            exeFile.set(null);
            deleteQuietly(file);
            ymlBytes.set(null);
            exeUpload.clearFileList();
            ymlUpload.clearFileList();
            uploadError.setText("");
            Notification.show("Релиз загружен", 4000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            refreshStudioHeader();
            refreshUploadState();
        } catch (ConflictException | PackageException | java.io.IOException ex) {
            String text = ex.getMessage() == null || ex.getMessage().isBlank() ? "Не удалось загрузить" : ex.getMessage();
            uploadError.setText(text);
            Notification.show(text, 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private static void deleteQuietly(File file) {
        try {
            Files.deleteIfExists(file.toPath());
        } catch (java.io.IOException ignored) {
            /* временный файл удалит ОС */
        }
    }
}
