package io.uitrainer.platform.view.home;

import io.jmix.core.DataManager;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonStatus;

import java.util.List;

/** Очереди уроков преподавателя: черновики, опубликованные, на проверке и возвращённые. */
public final class LessonBuckets {
    public static final String DRAFT = "draft";
    public static final String PUBLISHED = "published";
    public static final String REVIEW = "review";
    public static final String RETURNED = "returned";

    private LessonBuckets() {
    }

    public static boolean known(String bucket) {
        return DRAFT.equals(bucket) || PUBLISHED.equals(bucket) || REVIEW.equals(bucket) || RETURNED.equals(bucket);
    }

    public static String title(String bucket) {
        return switch (bucket) {
            case DRAFT -> "Черновики";
            case PUBLISHED -> "Опубликованные";
            case REVIEW -> "На проверке";
            case RETURNED -> "Возвращённые";
            default -> "Уроки";
        };
    }

    public static long count(DataManager dataManager, String bucket) {
        if (RETURNED.equals(bucket)) return returnedCount(dataManager);
        LessonStatus status = status(bucket);
        if (status == null) return 0;
        return dataManager.loadValue("select count(l) from uic_Lesson l where l.status = :status", Long.class)
                .parameter("status", status.getId())
                .one();
    }

    public static List<Lesson> list(DataManager dataManager, String bucket) {
        if (RETURNED.equals(bucket)) {
            return dataManager.load(Lesson.class)
                    .query("select l from uic_Lesson l where l.status = :draft and l.id in ("
                            + "select t.lesson.id from uic_LifecycleTransition t "
                            + "where t.fromStatus = :review and t.toStatus = :draft) "
                            + "order by l.createdDate desc")
                    .parameter("draft", LessonStatus.DRAFT.getId())
                    .parameter("review", LessonStatus.REVIEW.getId())
                    .fetchPlan(LessonBuckets::withCourseAndAuthor)
                    .list();
        }
        LessonStatus status = status(bucket);
        if (status == null) return List.of();
        return dataManager.load(Lesson.class)
                .query("select l from uic_Lesson l where l.status = :status order by l.createdDate desc")
                .parameter("status", status.getId())
                .fetchPlan(LessonBuckets::withCourseAndAuthor)
                .list();
    }

    private static long returnedCount(DataManager dataManager) {
        return dataManager.loadValue("select count(l) from uic_Lesson l where l.status = :draft and l.id in ("
                        + "select t.lesson.id from uic_LifecycleTransition t "
                        + "where t.fromStatus = :review and t.toStatus = :draft)", Long.class)
                .parameter("draft", LessonStatus.DRAFT.getId())
                .parameter("review", LessonStatus.REVIEW.getId())
                .one();
    }

    private static LessonStatus status(String bucket) {
        return switch (bucket) {
            case DRAFT -> LessonStatus.DRAFT;
            case PUBLISHED -> LessonStatus.PUBLISHED;
            case REVIEW -> LessonStatus.REVIEW;
            default -> null;
        };
    }

    static void withCourseAndAuthor(io.jmix.core.FetchPlanBuilder builder) {
        builder.addFetchPlan("_base")
                .add("author", "_instance_name")
                .add("course", "_instance_name")
                .add("currentVersion", "_base")
                .add("publishedVersion", "_base");
    }
}
