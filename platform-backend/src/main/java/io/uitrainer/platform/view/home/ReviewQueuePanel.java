package io.uitrainer.platform.view.home;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import io.jmix.core.DataManager;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.LifecycleTransition;
import io.uitrainer.platform.entity.User;
import io.uitrainer.platform.service.LessonLifecycleService;
import io.uitrainer.platform.view.lesson.StatusBadges;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Очередь приёмки: уроки на проверке и решения за сегодня. */
public class ReviewQueuePanel extends VerticalLayout {

    private final DataManager dataManager;
    private final LessonLifecycleService lifecycle;
    private final TimeSource timeSource;
    private final Button waitingButton = new Button();
    private final Button todayButton = new Button();
    private final Span empty = new Span();
    private final Grid<Row> grid = new Grid<>();
    private boolean todayMode;

    public ReviewQueuePanel(DataManager dataManager, LessonLifecycleService lifecycle, TimeSource timeSource) {
        this.dataManager = dataManager;
        this.lifecycle = lifecycle;
        this.timeSource = timeSource;
        setPadding(false);
        setWidthFull();
        setSpacing(true);

        waitingButton.setId("counterWaiting");
        todayButton.setId("counterToday");
        waitingButton.addClickListener(e -> {
            todayMode = false;
            reload();
        });
        todayButton.addClickListener(e -> {
            todayMode = true;
            reload();
        });
        HorizontalLayout counters = new HorizontalLayout(waitingButton, todayButton);
        counters.setWidthFull();

        empty.setId("reviewEmpty");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)");

        grid.setId("reviewQueue");
        grid.setWidthFull();
        grid.setMinHeight("16em");
        grid.addColumn(Row::title).setHeader("Урок").setFlexGrow(2);
        grid.addColumn(Row::course).setHeader("Курс").setFlexGrow(2);
        grid.addColumn(Row::teacher).setHeader("Преподаватель").setFlexGrow(2);
        grid.addColumn(Row::when).setHeader("Когда").setFlexGrow(1);
        grid.addColumn(Row::outcome).setHeader("Решение").setFlexGrow(1);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true).setFlexGrow(0);
        add(counters, empty, grid);
        reload();
    }

    public void reload() {
        OffsetDateTime from = startOfToday();
        OffsetDateTime to = from.plusDays(1);
        long waiting = dataManager.loadValue("select count(l) from uic_Lesson l where l.status = :status", Long.class)
                .parameter("status", LessonStatus.REVIEW.getId())
                .one();
        long today = dataManager.loadValue("select count(t) from uic_LifecycleTransition t "
                        + "where t.at >= :from and t.at < :to and t.fromStatus = :fromStatus "
                        + "and (t.toStatus = :published or t.toStatus = :draft)", Long.class)
                .parameter("from", from)
                .parameter("to", to)
                .parameter("fromStatus", LessonStatus.REVIEW.getId())
                .parameter("published", LessonStatus.PUBLISHED.getId())
                .parameter("draft", LessonStatus.DRAFT.getId())
                .one();
        waitingButton.setText("Ожидают: " + waiting);
        todayButton.setText("Принято или возвращено сегодня: " + today);
        waitingButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
        todayButton.removeThemeVariants(ButtonVariant.LUMO_PRIMARY);
        (todayMode ? todayButton : waitingButton).addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        List<Row> rows = todayMode ? loadToday() : loadWaiting();
        grid.setItems(rows);
        empty.setVisible(rows.isEmpty());
        empty.setText(todayMode
                ? "Сегодня ещё не было публикаций и возвратов."
                : "Очередь пуста. Это нормально: преподаватель может опубликовать урок сам, не отправляя его на проверку.");
    }

    private HorizontalLayout actions(Row row) {
        HorizontalLayout box = new HorizontalLayout();
        box.setSpacing(true);
        Button open = new Button("Открыть", e -> navigate("lessons/" + row.lessonId()));
        open.setId("open-" + row.lessonId());
        box.add(open);
        if (row.versionId() != null) {
            Button preview = new Button("Предпросмотр", e -> navigate("lessons/preview/" + row.versionId()));
            preview.setId("preview-" + row.lessonId());
            box.add(preview);
        }
        if (row.inReview()) {
            Button publish = new Button("Опубликовать", e -> change(row.lessonId(), LessonStatus.PUBLISHED, null));
            publish.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            publish.setId("publish-" + row.lessonId());
            Button back = new Button("Вернуть", e -> askReturn(row.lessonId()));
            back.addThemeVariants(ButtonVariant.LUMO_SMALL);
            back.setId("return-" + row.lessonId());
            box.add(publish, back);
        }
        return box;
    }

    private void askReturn(UUID lessonId) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Вернуть на доработку");
        dialog.setWidth("36em");
        TextArea comment = new TextArea("Комментарий");
        comment.setId("returnComment");
        comment.setWidthFull();
        comment.setPlaceholder("Без комментария вернуть нельзя");
        dialog.add(comment);
        Button confirm = new Button("Вернуть", e -> {
            String text = comment.getValue() == null ? "" : comment.getValue().trim();
            if (text.isEmpty()) {
                Notification.show("Чтобы вернуть урок, напишите комментарий", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            dialog.close();
            change(lessonId, LessonStatus.DRAFT, text);
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        confirm.setId("returnConfirm");
        dialog.getFooter().add(confirm, new Button("Отмена", e -> dialog.close()));
        dialog.open();
    }

    private void change(UUID lessonId, LessonStatus to, String comment) {
        try {
            lifecycle.transition(lessonId, to, comment);
            Notification.show("Статус: " + StatusBadges.label(to), 3000, Notification.Position.BOTTOM_END)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            reload();
        } catch (RuntimeException ex) {
            Notification.show(ex.getMessage() == null ? "Не удалось сменить статус" : ex.getMessage(),
                    5000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private List<Row> loadWaiting() {
        List<Lesson> lessons = dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.status = :status order by l.createdDate desc")
                .parameter("status", LessonStatus.REVIEW.getId())
                .fetchPlan(LessonBuckets::withCourseAndAuthor)
                .list();
        Map<UUID, OffsetDateTime> submitted = latestTo(lessons.stream().map(Lesson::getId).toList(), LessonStatus.REVIEW);
        List<Row> rows = new ArrayList<>();
        for (Lesson lesson : lessons) {
            OffsetDateTime at = submitted.getOrDefault(lesson.getId(), lesson.getCreatedDate());
            rows.add(row(lesson, at, "", true));
        }
        return rows;
    }

    private List<Row> loadToday() {
        OffsetDateTime from = startOfToday();
        List<LifecycleTransition> transitions = dataManager.load(LifecycleTransition.class)
                .query("select t from uic_LifecycleTransition t "
                        + "where t.at >= :from and t.at < :to and t.fromStatus = :fromStatus "
                        + "and (t.toStatus = :published or t.toStatus = :draft) order by t.at desc")
                .parameter("from", from)
                .parameter("to", from.plusDays(1))
                .parameter("fromStatus", LessonStatus.REVIEW.getId())
                .parameter("published", LessonStatus.PUBLISHED.getId())
                .parameter("draft", LessonStatus.DRAFT.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("lesson", "_base"))
                .list();
        List<UUID> ids = transitions.stream().map(t -> t.getLesson().getId()).distinct().toList();
        Map<UUID, Lesson> lessons = lessonsById(ids);
        List<Row> rows = new ArrayList<>();
        for (LifecycleTransition transition : transitions) {
            Lesson lesson = lessons.get(transition.getLesson().getId());
            if (lesson == null) continue;
            String outcome = transition.getToStatus() == LessonStatus.PUBLISHED ? "Опубликован" : "Возвращён";
            rows.add(row(lesson, transition.getAt(), outcome, false));
        }
        return rows;
    }

    private Map<UUID, OffsetDateTime> latestTo(List<UUID> ids, LessonStatus to) {
        Map<UUID, OffsetDateTime> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        List<LifecycleTransition> transitions = dataManager.load(LifecycleTransition.class)
                .query("select t from uic_LifecycleTransition t where t.lesson.id in :ids and t.toStatus = :to order by t.at desc")
                .parameter("ids", ids)
                .parameter("to", to.getId())
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("lesson", "_base"))
                .list();
        for (LifecycleTransition transition : transitions) {
            result.putIfAbsent(transition.getLesson().getId(), transition.getAt());
        }
        return result;
    }

    private Map<UUID, Lesson> lessonsById(List<UUID> ids) {
        Map<UUID, Lesson> result = new HashMap<>();
        if (ids.isEmpty()) return result;
        for (Lesson lesson : dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.id in :ids")
                .parameter("ids", ids)
                .fetchPlan(LessonBuckets::withCourseAndAuthor)
                .list()) {
            result.put(lesson.getId(), lesson);
        }
        return result;
    }

    private Row row(Lesson lesson, OffsetDateTime at, String outcome, boolean inReview) {
        UUID versionId = lesson.getCurrentVersion() == null ? null : lesson.getCurrentVersion().getId();
        return new Row(lesson.getId(), versionId, lesson.getTitle(),
                lesson.getCourse() == null ? "—" : lesson.getCourse().getTitle(),
                person(lesson.getAuthor()), StatusBadges.date(at), outcome, inReview);
    }

    private static String person(User user) {
        if (user == null) return "—";
        String name = user.getDisplayName();
        return name == null || name.isBlank() ? user.getUsername() : name;
    }

    private OffsetDateTime startOfToday() {
        ZonedDateTime start = timeSource.now().toOffsetDateTime().atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneId.systemDefault());
        return start.toOffsetDateTime();
    }

    private void navigate(String location) {
        getUI().ifPresent(ui -> ui.navigate(location));
    }

    private record Row(UUID lessonId, UUID versionId, String title, String course, String teacher,
                        String when, String outcome, boolean inReview) {
    }
}
