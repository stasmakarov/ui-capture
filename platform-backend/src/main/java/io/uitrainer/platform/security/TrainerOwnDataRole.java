package io.uitrainer.platform.security;

import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import io.uitrainer.platform.entity.*;

/** Тренер видит только свои уроки, их версии и журнал, свои токены, тикеты и курсы (владелец). */
@RowLevelRole(name = "Тренер: только свои данные", code = TrainerOwnDataRole.CODE)
public interface TrainerOwnDataRole {
    String CODE = "trainer-own-data";

    @JpqlRowLevelPolicy(entityClass = Lesson.class, where = "{E}.author.username = :current_user_username")
    void lesson();

    @JpqlRowLevelPolicy(entityClass = LessonVersion.class, where = "{E}.lesson.author.username = :current_user_username")
    void lessonVersion();

    @JpqlRowLevelPolicy(entityClass = LifecycleTransition.class, where = "{E}.lesson.author.username = :current_user_username")
    void transition();

    @JpqlRowLevelPolicy(entityClass = StudioToken.class, where = "{E}.owner.username = :current_user_username")
    void token();

    @JpqlRowLevelPolicy(entityClass = StudioTicket.class, where = "{E}.owner.username = :current_user_username")
    void ticket();

    @JpqlRowLevelPolicy(entityClass = Course.class, where = "{E}.owner.username = :current_user_username")
    void course();

    @JpqlRowLevelPolicy(entityClass = StudyGroup.class, where = "{E}.owner.username = :current_user_username")
    void studyGroup();

    @JpqlRowLevelPolicy(entityClass = GroupCourse.class, where = "{E}.group.owner.username = :current_user_username")
    void groupCourse();

    @JpqlRowLevelPolicy(entityClass = GroupMember.class, where = "{E}.group.owner.username = :current_user_username")
    void groupMember();

    @JpqlRowLevelPolicy(entityClass = GroupCourseProgress.class, where = "{E}.group.owner.username = :current_user_username")
    void groupCourseProgress();

    @JpqlRowLevelPolicy(entityClass = LessonAttempt.class,
            where = "{E}.student.id in (select m.student.id from uic_GroupMember m where m.group.owner.username = :current_user_username)")
    void lessonAttempt();
}
