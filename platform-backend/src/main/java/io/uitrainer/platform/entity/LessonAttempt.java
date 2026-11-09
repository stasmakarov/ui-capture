package io.uitrainer.platform.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UIC_LESSON_ATTEMPT", indexes = {
        @Index(name = "IDX_UIC_LESSON_ATTEMPT_STUDENT", columnList = "STUDENT_ID"),
        @Index(name = "IDX_UIC_LESSON_ATTEMPT_LESSON", columnList = "LESSON_ID"),
        @Index(name = "IDX_UIC_LESSON_ATTEMPT_VERSION", columnList = "LESSON_VERSION_ID"),
        @Index(name = "UQ_UIC_LESSON_ATTEMPT", columnList = "STUDENT_ID, LESSON_ID", unique = true)
})
@Entity(name = "uic_LessonAttempt")
public class LessonAttempt {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @JoinColumn(name = "STUDENT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User student;

    @JoinColumn(name = "LESSON_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Lesson lesson;

    @JoinColumn(name = "LESSON_VERSION_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private LessonVersion lessonVersion;

    @Column(name = "STARTED_AT", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public Lesson getLesson() { return lesson; }
    public void setLesson(Lesson lesson) { this.lesson = lesson; }
    public LessonVersion getLessonVersion() { return lessonVersion; }
    public void setLessonVersion(LessonVersion lessonVersion) { this.lessonVersion = lessonVersion; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
}
