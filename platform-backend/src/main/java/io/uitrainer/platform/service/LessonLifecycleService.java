package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;
import io.uitrainer.platform.entity.LifecycleTransition;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ЖЦ урока. Основной путь: черновик → опубликован, без проверки.
 * Проверка необязательна: черновик → на проверке → опубликован, либо возврат в черновик с комментарием.
 * Опубликованный урок можно архивировать. Каждый переход пишется в LifecycleTransition.
 */
@Service
public class LessonLifecycleService {

    private final DataManager dataManager;
    private final CurrentUserAccess access;
    private final CourseAccessService courseAccess;
    private final TimeSource timeSource;

    public LessonLifecycleService(DataManager dataManager, CurrentUserAccess access,
                                  CourseAccessService courseAccess, TimeSource timeSource) {
        this.dataManager = dataManager;
        this.access = access;
        this.courseAccess = courseAccess;
        this.timeSource = timeSource;
    }

    /** Порядок кнопок: публикация — основное действие, проверка — рядом, по желанию. */
    private static final List<LessonStatus> TRANSITION_ORDER = List.of(
            LessonStatus.PUBLISHED, LessonStatus.REVIEW, LessonStatus.DRAFT, LessonStatus.ARCHIVED);

    public static boolean isAllowedEdge(LessonStatus from, LessonStatus to) {
        return switch (from) {
            case DRAFT -> to == LessonStatus.PUBLISHED || to == LessonStatus.REVIEW;
            case REVIEW -> to == LessonStatus.PUBLISHED || to == LessonStatus.DRAFT;
            case PUBLISHED -> to == LessonStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    /** Переходы, доступные текущему пользователю для урока. */
    public List<LessonStatus> availableTransitions(Lesson lesson) {
        Lesson fresh = load(lesson.getId());
        List<LessonStatus> result = new ArrayList<>();
        for (LessonStatus to : TRANSITION_ORDER) {
            if (isAllowedEdge(fresh.getStatus(), to) && denialReason(fresh, to) == null) result.add(to);
        }
        return result;
    }

    @Transactional
    public Lesson transition(UUID lessonId, LessonStatus to, @Nullable String comment) {
        Lesson lesson = load(lessonId);
        LessonStatus from = lesson.getStatus();
        if (!isAllowedEdge(from, to)) {
            throw new IllegalStateException("Переход " + from + " → " + to + " не предусмотрен жизненным циклом урока");
        }
        String reason = denialReason(lesson, to);
        if (reason != null) throw new AccessException(reason);
        String text = comment == null ? null : comment.trim();
        if (from == LessonStatus.REVIEW && to == LessonStatus.DRAFT && (text == null || text.isEmpty())) {
            throw new IllegalArgumentException("Чтобы вернуть урок на доработку, напишите комментарий");
        }
        lesson.setStatus(to);
        if (to == LessonStatus.PUBLISHED) lesson.setPublishedVersion(lesson.getCurrentVersion());

        LifecycleTransition transition = dataManager.create(LifecycleTransition.class);
        transition.setLesson(lesson);
        transition.setLessonVersion(lesson.getCurrentVersion());
        transition.setFromStatus(from);
        transition.setToStatus(to);
        transition.setActor(access.authenticatedUsername());
        transition.setAt(timeSource.now().toOffsetDateTime());
        transition.setComment(text == null || text.isEmpty() ? null : text);
        LessonAccessListener.runLifecycleSave(() ->
                dataManager.unconstrained().save(new SaveContext().saving(lesson, transition)));
        return load(lessonId);
    }

    /** null — можно; иначе причина отказа. */
    @Nullable
    String denialReason(Lesson lesson, LessonStatus to) {
        if (access.isAdmin()) return null;
        boolean trainerAuthor = access.isTrainer() && (lesson.getCourse() == null
                ? lesson.getAuthor() != null && access.username().equals(lesson.getAuthor().getUsername())
                : courseAccess.canEdit(lesson.getCourse()));
        boolean reviewer = access.isReviewer();
        LessonStatus from = lesson.getStatus();
        if (from == LessonStatus.DRAFT && to == LessonStatus.PUBLISHED) {
            return trainerAuthor ? null : "Опубликовать черновик может преподаватель — автор урока";
        }
        if (from == LessonStatus.DRAFT && to == LessonStatus.REVIEW) {
            return trainerAuthor ? null : "Отправить на проверку может только автор урока";
        }
        if (from == LessonStatus.REVIEW && to == LessonStatus.PUBLISHED) {
            return reviewer || trainerAuthor ? null : "Опубликовать урок на проверке может проверяющий или сам автор";
        }
        if (from == LessonStatus.REVIEW && to == LessonStatus.DRAFT) {
            return reviewer || trainerAuthor ? null : "Вернуть на доработку может проверяющий или автор";
        }
        if (from == LessonStatus.PUBLISHED && to == LessonStatus.ARCHIVED) {
            return reviewer || trainerAuthor ? null : "Архивировать может проверяющий или автор";
        }
        return "Переход недоступен";
    }

    private Lesson load(UUID id) {
        return dataManager.unconstrained().load(Lesson.class).id(id)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("course", "_base")
                        .add("currentVersion", "_base").add("publishedVersion", "_base"))
                .one();
    }
}
