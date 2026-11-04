package io.uicapture.platform.view.lesson;

import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinServletRequest;
import io.jmix.core.DataManager;
import io.jmix.core.FluentLoader;
import io.jmix.flowui.view.StandardView;
import io.jmix.flowui.view.ViewController;
import io.uicapture.platform.entity.Course;
import io.uicapture.platform.entity.Lesson;
import io.uicapture.platform.entity.LessonStatus;
import io.uicapture.platform.security.PlatformViews;
import io.uicapture.platform.service.CurrentUserAccess;
import io.uicapture.platform.service.StudioTokenService;
import io.uicapture.platform.view.main.MainView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Список уроков: фильтр по статусу, «Записать в студии». */
@Route(value = "lessons", layout = MainView.class)
@ViewController(id = PlatformViews.LESSON_LIST)
public class LessonListView extends StandardView {

    /**
     * Открыть студию: сначала схема uicapture://, затем (если окно не потеряло фокус) пинг локальной студии
     * и передача ей того же задания через её API. Возвращает scheme | local | none.
     */
    static final String LAUNCH_JS = """
            const link = $0, server = $1, ticket = $2, course = $3, studio = $4, forceFallback = $5;
            return new Promise((resolve) => {
              let left = false;
              const onBlur = () => { left = true; };
              window.addEventListener('blur', onBlur);
              let frame = null;
              if (!forceFallback) {
                frame = document.createElement('iframe');
                frame.style.display = 'none';
                frame.src = link;
                document.body.appendChild(frame);
              }
              setTimeout(async () => {
                window.removeEventListener('blur', onBlur);
                if (frame) frame.remove();
                if (left && !forceFallback) { resolve('scheme'); return; }
                try {
                  await fetch(studio + '/api/health', { mode: 'no-cors', cache: 'no-store' });
                  await fetch(studio + '/api/platform/link', {
                    method: 'POST', mode: 'no-cors', headers: { 'Content-Type': 'text/plain' },
                    body: JSON.stringify({ server, ticket, course })
                  });
                  window.open(studio + '/?linked=1', '_blank');
                  resolve('local');
                } catch (e) {
                  resolve('none');
                }
              }, forceFallback ? 50 : 1500);
            });
            """;

    @Autowired
    private DataManager dataManager;
    @Autowired
    private StudioTokenService tokenService;
    @Autowired
    private CurrentUserAccess access;
    @Value("${uicapture.public-url:}")
    private String publicUrl;
    @Value("${uicapture.studio.local-url:http://127.0.0.1:44731}")
    private String studioUrl;

    private final Grid<Lesson> grid = new Grid<>(Lesson.class, false);
    private final ComboBox<LessonStatus> statusFilter = new ComboBox<>("Статус");
    private final ComboBox<Course> courseField = new ComboBox<>("Курс для записи");

    public LessonListView() {
        getContent().setSizeFull();
        getContent().setPadding(true);
    }

    @Override
    public String getPageTitle() {
        return "Уроки";
    }

    @io.jmix.flowui.view.Subscribe
    public void onInit(InitEvent event) {
        statusFilter.setItems(LessonStatus.values());
        statusFilter.setItemLabelGenerator(StatusBadges::label);
        statusFilter.setClearButtonVisible(true);
        statusFilter.setPlaceholder("Все");
        statusFilter.setId("statusFilter");
        statusFilter.addValueChangeListener(e -> reload());

        HorizontalLayout toolbar = new HorizontalLayout(statusFilter);
        toolbar.setWidthFull();
        toolbar.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.END);

        if (access.isTrainer() || access.isAdmin()) {
            List<Course> courses = dataManager.load(Course.class).query("select c from uic_Course c order by c.title").list();
            courseField.setItems(courses);
            courseField.setItemLabelGenerator(Course::getTitle);
            courseField.setWidth("22em");
            courseField.setId("courseField");
            if (!courses.isEmpty()) courseField.setValue(courses.get(0));
            Button record = new Button("Записать в студии", VaadinIcon.CIRCLE.create(), e -> launchStudio(false));
            record.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            record.setId("recordInStudio");
            Button fallback = new Button("Студия уже открыта на этом компьютере", e -> launchStudio(true));
            fallback.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            fallback.setId("recordInStudioLocal");
            toolbar.add(courseField, record, fallback);
        }
        Button refresh = new Button("Обновить", VaadinIcon.REFRESH.create(), e -> reload());
        refresh.setId("refreshLessons");
        toolbar.add(refresh);

        grid.addColumn(Lesson::getTitle).setHeader("Урок").setFlexGrow(3).setSortable(true);
        grid.addColumn(Lesson::getExternalId).setHeader("id урока").setFlexGrow(2);
        grid.addColumn(l -> l.getAuthor() == null ? "" : l.getAuthor().getDisplayName()).setHeader("Автор").setFlexGrow(2);
        grid.addComponentColumn(l -> StatusBadges.badge(l.getStatus())).setHeader("Статус").setFlexGrow(1);
        grid.addColumn(l -> l.getCurrentVersion() == null ? "" : "v" + l.getCurrentVersion().getVersionNo()).setHeader("Версия").setFlexGrow(0).setWidth("6em");
        grid.addColumn(l -> l.getCourse() == null ? "" : l.getCourse().getTitle()).setHeader("Курс").setFlexGrow(2);
        grid.addColumn(l -> StatusBadges.date(l.getCreatedDate())).setHeader("Создан").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
        grid.setId("lessonsGrid");
        grid.addItemClickListener(e -> openLesson(e.getItem()));

        getContent().add(toolbar, grid);
        reload();
    }

    private void openLesson(Lesson lesson) {
        if (access.isStudent() && !access.isTrainer() && !access.isReviewer() && !access.isAdmin()) {
            if (lesson.getPublishedVersion() != null) {
                getUI().ifPresent(ui -> ui.navigate("lessons/preview/" + lesson.getPublishedVersion().getId()));
            }
            return;
        }
        getUI().ifPresent(ui -> ui.navigate("lessons/" + lesson.getId()));
    }

    private void reload() {
        LessonStatus status = statusFilter.getValue();
        String query = "select l from uic_Lesson l" + (status == null ? "" : " where l.status = :status") + " order by l.createdDate desc";
        FluentLoader.ByQuery<Lesson> loader = dataManager.load(Lesson.class).query(query)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("course", "_base")
                        .add("currentVersion", "_base").add("publishedVersion", "_base"));
        if (status != null) loader.parameter("status", status.getId());
        grid.setItems(loader.list());
    }

    private void launchStudio(boolean forceFallback) {
        Course course = courseField.getValue();
        String ticket = tokenService.issueTicket(course == null ? null : course.getId());
        String server = serverUrl();
        String courseId = course == null ? "" : course.getId().toString();
        String link = "uicapture://new?course=" + enc(courseId) + "&server=" + enc(server) + "&ticket=" + enc(ticket);
        getUI().ifPresent(ui -> ui.getPage().executeJs(LAUNCH_JS, link, server, ticket, courseId, studioUrl, forceFallback)
                .then(String.class, result -> onLaunchResult(result, link)));
    }

    private void onLaunchResult(String result, String link) {
        if ("scheme".equals(result)) {
            Notification.show("Студия открыта по ссылке uicapture://. Запишите урок и нажмите «Опубликовать на сервер».", 6000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else if ("local".equals(result)) {
            Notification.show("Локальная студия (127.0.0.1:44731) получила задание и открыта в новой вкладке.", 6000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        } else {
            Dialog dialog = new Dialog();
            dialog.setHeaderTitle("Студия не найдена");
            TextField linkField = new TextField("Ссылка для студии (действует 5 минут)");
            linkField.setValue(link);
            linkField.setReadOnly(true);
            linkField.setWidthFull();
            dialog.add(new Paragraph("Не удалось открыть uicapture:// и не отвечает локальная студия на 127.0.0.1:44731."),
                    new Html("<ol><li>Установите студию: <code>git clone</code> репозитория ui-capture, <code>npm install</code>.</li>"
                            + "<li>Запустите: <code>npm start</code> (откроется http://127.0.0.1:44731).</li>"
                            + "<li>Нажмите «Записать в студии» ещё раз — или выполните <code>npx ui-capture open-url \"…\"</code> со ссылкой ниже.</li></ol>"),
                    linkField);
            dialog.setWidth("40em");
            dialog.getFooter().add(new Button("Закрыть", e -> dialog.close()));
            dialog.open();
        }
    }

    private String serverUrl() {
        if (publicUrl != null && !publicUrl.isBlank()) return publicUrl.replaceAll("/+$", "");
        HttpServletRequest request = VaadinServletRequest.getCurrent().getHttpServletRequest();
        int port = request.getServerPort();
        boolean defaultPort = (port == 80 && "http".equals(request.getScheme())) || (port == 443 && "https".equals(request.getScheme()));
        return request.getScheme() + "://" + request.getServerName() + (defaultPort ? "" : ":" + port) + request.getContextPath();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
