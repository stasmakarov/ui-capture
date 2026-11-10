package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.FetchPlan;
import io.jmix.core.SaveContext;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.Lesson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LessonOrderService {

    private final DataManager dataManager;

    public LessonOrderService(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    public List<Lesson> orderedLessons(UUID courseId) {
        List<Lesson> lessons = dataManager.unconstrained().load(Lesson.class)
                .query("select l from uic_Lesson l where l.course.id = :c order by l.sortOrder, l.createdDate")
                .parameter("c", courseId)
                .fetchPlan(fp -> fp.addFetchPlan(FetchPlan.BASE).add("course", FetchPlan.INSTANCE_NAME))
                .list();
        return SortOrders.sorted(lessons, Lesson::getSortOrder);
    }

    public int nextSortOrder(Course course) {
        if (course == null || course.getId() == null) {
            return nextFromList(course == null ? List.of() : course.getLessons());
        }
        Integer max = dataManager.unconstrained().loadValue(
                        "select max(l.sortOrder) from uic_Lesson l where l.course.id = :c", Integer.class)
                .parameter("c", course.getId())
                .optional()
                .orElse(null);
        int result = max == null ? 0 : max;
        if (course.getLessons() != null) {
            for (Lesson lesson : course.getLessons()) {
                if (lesson.getSortOrder() != null) {
                    result = Math.max(result, lesson.getSortOrder());
                }
            }
        }
        return result + 1;
    }

    @Transactional
    public void moveLesson(Course course, Lesson selected, int delta) {
        List<Lesson> ordered = orderedLessons(course.getId());
        if (!SortOrders.move(ordered, selectedById(ordered, selected), delta)) {
            return;
        }
        SortOrders.normalize(ordered, Lesson::setSortOrder);
        dataManager.unconstrained().save(new SaveContext().saving(ordered.toArray()));
    }

    @Transactional
    public void normalizeCourse(UUID courseId) {
        if (courseId == null) {
            return;
        }
        List<Lesson> ordered = orderedLessons(courseId);
        SortOrders.normalize(ordered, Lesson::setSortOrder);
        if (!ordered.isEmpty()) {
            dataManager.unconstrained().save(new SaveContext().saving(ordered.toArray()));
        }
    }

    private int nextFromList(List<Lesson> lessons) {
        if (lessons == null) {
            return 1;
        }
        return SortOrders.next(lessons.stream().map(Lesson::getSortOrder).toList());
    }

    private Lesson selectedById(List<Lesson> ordered, Lesson selected) {
        for (Lesson lesson : ordered) {
            if (lesson.getId().equals(selected.getId())) {
                return lesson;
            }
        }
        return selected;
    }
}
