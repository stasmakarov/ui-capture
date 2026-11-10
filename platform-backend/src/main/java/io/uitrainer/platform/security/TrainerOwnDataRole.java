package io.uitrainer.platform.security;

import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import io.uitrainer.platform.entity.*;

/** Тренер видит свои курсы, соавторство и курсы для ведения; черновики — владелец и соавторы. */
@RowLevelRole(name = "Тренер: только свои данные", code = TrainerOwnDataRole.CODE)
public interface TrainerOwnDataRole {
    String CODE = "trainer-own-data";

    String COURSE_VISIBLE = "{E}.owner.username = :current_user_username "
            + "or exists (select a from uic_CourseCoauthor a where a.course = {E} and a.trainer.username = :current_user_username) "
            + "or {E}.sharing = 'ALL_TEACHERS' "
            + "or ({E}.sharing = 'SELECTED' and exists (select s from uic_CourseSharedWith s where s.course = {E} and s.trainer.username = :current_user_username))";

    String LESSON_VISIBLE = "({E}.course is null and {E}.author.username = :current_user_username) "
            + "or exists (select c from uic_Course c, uic_Lesson l where l.id = {E}.id and l.course.id = c.id "
            + "and c.owner.username = :current_user_username) "
            + "or exists (select a from uic_CourseCoauthor a, uic_Lesson l where l.id = {E}.id and a.course.id = l.course.id "
            + "and a.trainer.username = :current_user_username) "
            + "or ({E}.status = 'PUBLISHED' and exists (select c from uic_Course c, uic_Lesson l where l.id = {E}.id "
            + "and l.course.id = c.id and (c.sharing = 'ALL_TEACHERS' "
            + "or (c.sharing = 'SELECTED' and exists (select s from uic_CourseSharedWith s where s.course.id = c.id "
            + "and s.trainer.username = :current_user_username)))))";

    String NESTED_LESSON_VISIBLE = "({E}.lesson.course is null and {E}.lesson.author.username = :current_user_username) "
            + "or exists (select c from uic_Course c, uic_Lesson l where l.id = {E}.lesson.id and l.course.id = c.id "
            + "and c.owner.username = :current_user_username) "
            + "or exists (select a from uic_CourseCoauthor a, uic_Lesson l where l.id = {E}.lesson.id and a.course.id = l.course.id "
            + "and a.trainer.username = :current_user_username) "
            + "or ({E}.lesson.status = 'PUBLISHED' and exists (select c from uic_Course c, uic_Lesson l where l.id = {E}.lesson.id "
            + "and l.course.id = c.id and (c.sharing = 'ALL_TEACHERS' "
            + "or (c.sharing = 'SELECTED' and exists (select s from uic_CourseSharedWith s where s.course.id = c.id "
            + "and s.trainer.username = :current_user_username)))))";

    String CHILD_COURSE_VISIBLE = "{E}.course.owner.username = :current_user_username "
            + "or exists (select a from uic_CourseCoauthor a where a.course = {E}.course and a.trainer.username = :current_user_username) "
            + "or {E}.course.sharing = 'ALL_TEACHERS' "
            + "or ({E}.course.sharing = 'SELECTED' and exists (select s from uic_CourseSharedWith s where s.course = {E}.course "
            + "and s.trainer.username = :current_user_username))";

    @JpqlRowLevelPolicy(entityClass = Lesson.class, where = LESSON_VISIBLE)
    void lesson();

    @JpqlRowLevelPolicy(entityClass = LessonVersion.class, where = NESTED_LESSON_VISIBLE)
    void lessonVersion();

    @JpqlRowLevelPolicy(entityClass = LifecycleTransition.class, where = NESTED_LESSON_VISIBLE)
    void transition();

    @JpqlRowLevelPolicy(entityClass = StudioToken.class, where = "{E}.owner.username = :current_user_username")
    void token();

    @JpqlRowLevelPolicy(entityClass = StudioTicket.class, where = "{E}.owner.username = :current_user_username")
    void ticket();

    @JpqlRowLevelPolicy(entityClass = Course.class, where = COURSE_VISIBLE)
    void course();

    @JpqlRowLevelPolicy(entityClass = CourseCoauthor.class, where = CHILD_COURSE_VISIBLE)
    void courseCoauthor();

    @JpqlRowLevelPolicy(entityClass = CourseSharedWith.class, where = CHILD_COURSE_VISIBLE)
    void courseSharedWith();

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
