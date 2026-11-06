package io.uitrainer.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UIC_LIFECYCLE_TRANSITION")
@Entity(name = "uic_LifecycleTransition")
public class LifecycleTransition {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @CreatedBy
    @Column(name = "CREATED_BY")
    private String createdBy;

    @CreatedDate
    @Column(name = "CREATED_DATE")
    private OffsetDateTime createdDate;

    @OnDeleteInverse(DeletePolicy.DENY)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LESSON_ID", nullable = false)
    private Lesson lesson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "LESSON_VERSION_ID")
    private LessonVersion lessonVersion;

    @Column(name = "FROM_STATUS", length = 20)
    private String fromStatus;

    @Column(name = "TO_STATUS", nullable = false, length = 20)
    private String toStatus;

    @InstanceName
    @Column(name = "ACTOR", nullable = false)
    private String actor;

    @Column(name = "AT", nullable = false)
    private OffsetDateTime at;

    @Lob
    @Column(name = "COMMENT_")
    private String comment;

    public Lesson getLesson() { return lesson; }
    public void setLesson(Lesson lesson) { this.lesson = lesson; }
    public LessonVersion getLessonVersion() { return lessonVersion; }
    public void setLessonVersion(LessonVersion lessonVersion) { this.lessonVersion = lessonVersion; }
    public LessonStatus getFromStatus() { return fromStatus == null ? null : LessonStatus.fromId(fromStatus); }
    public void setFromStatus(LessonStatus fromStatus) { this.fromStatus = fromStatus == null ? null : fromStatus.getId(); }
    public LessonStatus getToStatus() { return toStatus == null ? null : LessonStatus.fromId(toStatus); }
    public void setToStatus(LessonStatus toStatus) { this.toStatus = toStatus == null ? null : toStatus.getId(); }
    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }
    public OffsetDateTime getAt() { return at; }
    public void setAt(OffsetDateTime at) { this.at = at; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
}
