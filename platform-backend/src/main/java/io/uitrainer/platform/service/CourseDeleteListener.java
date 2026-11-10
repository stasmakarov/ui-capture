package io.uitrainer.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.DeletePolicyException;
import io.jmix.data.listener.BeforeDeleteEntityListener;
import io.uitrainer.platform.entity.Course;
import org.springframework.stereotype.Component;

@Component("uic_CourseDeleteListener")
public class CourseDeleteListener implements BeforeDeleteEntityListener<Course> {

    private final DataManager dataManager;

    public CourseDeleteListener(DataManager dataManager) {
        this.dataManager = dataManager;
    }

    @Override
    public void onBeforeDelete(Course course) {
        if (course.getId() == null) {
            return;
        }
        Long lessons = dataManager.unconstrained().loadValue(
                        "select count(l) from uic_Lesson l where l.course.id = :c", Long.class)
                .parameter("c", course.getId())
                .one();
        if (lessons != null && lessons > 0) {
            throw new DeletePolicyException("uic_Course", "uic_Lesson");
        }
        Long assigned = dataManager.unconstrained().loadValue(
                        "select count(gc) from uic_GroupCourse gc where gc.course.id = :c", Long.class)
                .parameter("c", course.getId())
                .one();
        if (assigned != null && assigned > 0) {
            throw new DeletePolicyException("uic_Course", "uic_GroupCourse");
        }
    }
}
