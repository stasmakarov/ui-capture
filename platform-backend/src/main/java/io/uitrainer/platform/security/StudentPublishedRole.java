package io.uitrainer.platform.security;

import io.jmix.security.role.annotation.JpqlRowLevelPolicy;
import io.jmix.security.role.annotation.RowLevelRole;
import io.uitrainer.platform.entity.Lesson;
import io.uitrainer.platform.entity.LessonVersion;

/** Ученик видит только опубликованные уроки и их опубликованные версии. */
@RowLevelRole(name = "Ученик: только опубликованное", code = StudentPublishedRole.CODE)
public interface StudentPublishedRole {
    String CODE = "student-published";

    @JpqlRowLevelPolicy(entityClass = Lesson.class, where = "{E}.status = 'PUBLISHED'")
    void lesson();

    @JpqlRowLevelPolicy(entityClass = LessonVersion.class, where = "{E}.lesson.status = 'PUBLISHED' and {E}.lesson.publishedVersion.id = {E}.id")
    void lessonVersion();
}
