package io.uitrainer.platform.security;

import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import io.uitrainer.platform.entity.Course;
import io.uitrainer.platform.entity.GroupCourse;
import io.uitrainer.platform.entity.GroupCourseProgress;
import io.uitrainer.platform.entity.GroupMember;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonAttempt;
import io.uitrainer.platform.entity.LessonVersion;
import io.uitrainer.platform.entity.StudyGroup;

/** Ученик видит только опубликованные уроки и их опубликованные версии. */
@RowLevelRole(name = "Ученик: только опубликованное", code = StudentPublishedRole.CODE)
public interface StudentPublishedRole {
    String CODE = "student-published";

    @JpqlRowLevelPolicy(entityClass = Lesson.class, where = "{E}.status = 'PUBLISHED'")
    void lesson();

    @JpqlRowLevelPolicy(entityClass = LessonVersion.class, where = "{E}.lesson.status = 'PUBLISHED' and {E}.lesson.publishedVersion.id = {E}.id")
    void lessonVersion();

    @JpqlRowLevelPolicy(entityClass = StudyGroup.class,
            where = "{E}.status = 'ACTIVE' and {E}.id in (select m.group.id from uic_GroupMember m where m.student.username = :current_user_username)")
    void studyGroup();

    @JpqlRowLevelPolicy(entityClass = GroupMember.class, where = "{E}.student.username = :current_user_username")
    void groupMember();

    @JpqlRowLevelPolicy(entityClass = GroupCourse.class,
            where = "{E}.group.status = 'ACTIVE' and {E}.group.id in (select m.group.id from uic_GroupMember m where m.student.username = :current_user_username)")
    void groupCourse();

    @JpqlRowLevelPolicy(entityClass = GroupCourseProgress.class, where = "{E}.student.username = :current_user_username")
    void groupCourseProgress();

    @JpqlRowLevelPolicy(entityClass = LessonAttempt.class, where = "{E}.student.username = :current_user_username")
    void lessonAttempt();

    @JpqlRowLevelPolicy(entityClass = Course.class,
            where = "{E}.id in (select gc.course.id from uic_GroupCourse gc, uic_GroupMember m "
                    + "where gc.group = m.group and m.student.username = :current_user_username and gc.group.status = 'ACTIVE')")
    void course();
}
