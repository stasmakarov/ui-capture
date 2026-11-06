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
 * ЖЦ урока: draft → review → published → archived; review → draft только с комментарием.
 * Самоутверждение: автор-тренер может сам перевести свой урок из review в published.
 * Каждый переход пишется в LifecycleTransition.
 */
@Service
public class LessonLifecycleService {

    private final DataManager dataManager;
    private final CurrentUserAccess access;
    private final TimeSource timeSource;

    public LessonLifecycleService(DataManager dataManager, CurrentUserAccess access, TimeSource timeSource) {
        this.dataManager = dataManager;
        this.access = access;
        this.timeSource = timeSource;
    }

    public static boolean isAllowedEdge(LessonStatus from, LessonStatus to) {
        return switch (from) {
            case DRAFT -> to == LessonStatus.REVIEW;
            case REVIEW -> to == LessonStatus.PUBLISHED || to == LessonStatus.DRAFT;
            case PUBLISHED -> to == LessonStatus.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    /** Переходы, доступные текущему пользователю для урока. */
    public List<LessonStatus> availableTransitions(Lesson lesson) {
        Lesson fresh = load(lesson.getId());
        List<LessonStatus> result = new ArrayList<>();
        for (LessonStatus to : LessonStatus.values()) {
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
        transition.setActor(access.username());
        transition.setAt(timeSource.now().toOffsetDateTime());
        transition.setComment(text == null || text.isEmpty() ? null : text);
        dataManager.unconstrained().save(new SaveContext().saving(lesson, transition));
        return load(lessonId);
    }

    /** null — можно; иначе причина отказа. */
    @Nullable
    String denialReason(Lesson lesson, LessonStatus to) {
        if (access.isAdmin()) return null;
        boolean author = lesson.getAuthor() != null && lesson.getAuthor().getUsername().equals(access.username());
        boolean trainerAuthor = author && access.isTrainer();
        boolean reviewer = access.isReviewer();
        LessonStatus from = lesson.getStatus();
        if (from == LessonStatus.DRAFT && to == LessonStatus.REVIEW) {
            return trainerAuthor ? null : "Отправить на проверку может только автор урока";
        }
        if (from == LessonStatus.REVIEW && to == LessonStatus.PUBLISHED) {
            return reviewer || trainerAuthor ? null : "Опубликовать может проверяющий или сам автор (самоутверждение)";
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
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("currentVersion", "_base").add("publishedVersion", "_base"))
                .one();
    }
}
