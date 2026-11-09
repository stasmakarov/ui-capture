package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import com.vaadin.flow.server.streams.UploadHandler;
import io.jmix.core.DataManager;
import io.jmix.core.TimeSource;
import io.jmix.flowui.ViewNavigators;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uitrainer.platform.api.ApiVersion;
import io.uitrainer.platform.entity.StudioRelease;
import io.uitrainer.platform.security.PlatformViews;
import io.uitrainer.platform.service.ConflictException;
import io.uitrainer.platform.service.CurrentUserAccess;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.service.PackageException;
import io.uitrainer.platform.service.StudioReleaseService;
import io.uitrainer.platform.service.UserProvisioning;
import io.uitrainer.platform.view.main.MainView;
import io.uitrainer.platform.view.user.TeacherDialogs;
import io.uitrainer.platform.view.user.UserListView;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

/** Кабинет платформы: преподаватели, пользователи, роли и очередь проверки. Токены — в меню администрирования. */
@Route(value = "cabinet/admin", layout = MainView.class)
@ViewController(id = PlatformViews.ADMIN_HOME)
public class AdminHomeView extends StandardView {

    @Autowired
    private DataManager dataManager;
    @Autowired
    private LessonLifecycleService lifecycle;
    @Autowired
    private TimeSource timeSource;
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

    private TeacherList teachers;

    public AdminHomeView() {
        getContent().setWidthFull();
        getContent().setPadding(true);
        getContent().setSpacing(true);
    }

    @Override
    public String getPageTitle() {
        return "Кабинет платформы";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        H2 title = new H2("Кабинет платформы");
        title.getStyle().set("margin", "0");
        Span lead = new Span("Преподаватели — это пользователи с ролью тренера. Отдельного справочника проверяющих нет.");
        lead.getStyle().set("color", "var(--lumo-secondary-text-color)");

        Button create = new Button("Создать преподавателя", e -> TeacherDialogs.createTeacher(provisioning, this::reloadTeachers));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setId("createTeacher");
        Button users = new Button("Пользователи", e -> viewNavigators.view(this, UserListView.class).navigate());
        users.setId("openUsers");
        Button roles = new Button("Роли", e -> viewNavigators.view(this, "sec_ResourceRoleModel.list").navigate());
        roles.setId("openRoles");
        Button rowRoles = new Button("Роли уровня строк", e -> viewNavigators.view(this, "sec_RowLevelRoleModel.list").navigate());
        rowRoles.setId("openRowRoles");
        HorizontalLayout links = new HorizontalLayout(create, users, roles, rowRoles);
        links.setWidthFull();
        links.getStyle().set("flex-wrap", "wrap");

        teachers = new TeacherList(dataManager, row -> getUI().ifPresent(ui -> ui.navigate(
                TeacherCoursesView.class, new RouteParameters("username", row.username()))));
        teachers.reload();

        H3 teachersTitle = new H3("Преподаватели");
        teachersTitle.getStyle().set("margin", "0");
        H3 queueTitle = new H3("Очередь проверки");
        queueTitle.getStyle().set("margin", "0");
        getContent().add(title, lead, links, versionsBlock(), teachersTitle, teachers, queueTitle,
                new ReviewQueuePanel(dataManager, lifecycle, timeSource));
    }

    private VerticalLayout versionsBlock() {
        VerticalLayout box = new VerticalLayout();
        box.setPadding(false);
        box.setSpacing(true);
        H3 heading = new H3("Версии");
        heading.getStyle().set("margin", "0");
        String platformVersion = buildProperties == null ? "0.6.0" : buildProperties.getVersion();
        box.add(heading, new Span("Платформа " + platformVersion + " · API " + ApiVersion.CURRENT));
        var current = releases.current(StudioReleaseService.WIN);
        if (current.isEmpty()) {
            Button download = new Button("Скачать студию");
            download.setId("downloadStudio");
            download.setEnabled(false);
            download.getElement().setAttribute("title", "Релиз студии ещё не загружен");
            box.add(new Span("Релиз студии ещё не загружен"), download);
        } else {
            StudioRelease release = current.get();
            Anchor download = new Anchor("/api/studio/releases/win/" + release.getFileName(), "Скачать студию");
            download.setId("downloadStudio");
            // Ссылка на REST, а не на view: без этого роутер Vaadin пытается открыть её как маршрут.
            download.setRouterIgnore(true);
            download.getElement().setAttribute("download", true);
            box.add(new Span("Студия " + release.getVersionName()), download);
        }
        box.add(uploadForm());
        return box;
    }

    private VerticalLayout uploadForm() {
        VerticalLayout form = new VerticalLayout();
        form.setPadding(false);
        form.setSpacing(true);
        form.add(new Span("Загрузить релиз"));
        TextField version = new TextField("version");
        version.setId("studioReleaseVersion");
        version.setWidth("16em");
        // .exe (~150 МБ) пишется во временный файл и отдаётся сервису потоком, в памяти не держим.
        AtomicReference<File> exeFile = new AtomicReference<>();
        AtomicReference<String> exeName = new AtomicReference<>();
        Upload exe = new Upload(UploadHandler.toTempFile((metadata, file) -> {
            File previous = exeFile.getAndSet(file);
            if (previous != null) deleteQuietly(previous);
            exeName.set(metadata.fileName());
        }));
        exe.setAcceptedFileTypes(".exe");
        exe.setMaxFiles(1);
        AtomicReference<byte[]> ymlBytes = new AtomicReference<>();
        Upload yml = new Upload(UploadHandler.inMemory((metadata, data) -> ymlBytes.set(data)));
        yml.setAcceptedFileTypes(".yml", ".yaml");
        yml.setMaxFiles(1);
        Button send = new Button("Загрузить", e -> {
            File file = exeFile.get();
            if (file == null) {
                Notification.show("Нужен файл .exe", 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            try {
                try (InputStream exeStream = new FileInputStream(file)) {
                    releases.uploadWin(version.getValue(), exeName.get(), exeStream, ymlBytes.get(), access.username());
                }
                exeFile.set(null);
                deleteQuietly(file);
                Notification.show("Релиз загружен", 4000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.getPage().reload());
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
        form.add(version, exe, yml, send);
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

    private void reloadTeachers() {
        if (teachers != null) teachers.reload();
    }
}
