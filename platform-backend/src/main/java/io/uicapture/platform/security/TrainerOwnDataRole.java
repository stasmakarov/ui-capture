package io.uicapture.platform.security;

import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import io.uicapture.platform.entity.*;

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
}
