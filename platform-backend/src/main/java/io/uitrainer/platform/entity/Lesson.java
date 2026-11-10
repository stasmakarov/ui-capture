package io.uitrainer.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.Listeners;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Listeners("uic_LessonAccessListener")
@Table(name = "UIC_LESSON", indexes = {@Index(name = "IDX_UIC_LESSON_EXTERNAL_ID", columnList = "EXTERNAL_ID", unique = true)})
@Entity(name = "uic_Lesson")
public class Lesson {
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

    /** id из lesson.json */
    @Column(name = "EXTERNAL_ID", nullable = false, unique = true, length = 64)
    private String externalId;

    @InstanceName
    @Column(name = "TITLE", nullable = false)
    private String title;

    @Column(name = "SOURCE_SITE")
    private String sourceSite;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "AUTHOR_ID", nullable = false)
    private User author;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = LessonStatus.DRAFT.getId();

    @OnDeleteInverse(DeletePolicy.DENY)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "COURSE_ID")
    private Course course;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CURRENT_VERSION_ID")
    private LessonVersion currentVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "PUBLISHED_VERSION_ID")
    private LessonVersion publishedVersion;

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceSite() { return sourceSite; }
    public void setSourceSite(String sourceSite) { this.sourceSite = sourceSite; }
    public User getAuthor() { return author; }
    public void setAuthor(User author) { this.author = author; }
    public LessonStatus getStatus() { return status == null ? null : LessonStatus.fromId(status); }
    public void setStatus(LessonStatus status) { this.status = status == null ? null : status.getId(); }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public LessonVersion getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(LessonVersion currentVersion) { this.currentVersion = currentVersion; }
    public LessonVersion getPublishedVersion() { return publishedVersion; }
    public void setPublishedVersion(LessonVersion publishedVersion) { this.publishedVersion = publishedVersion; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
}
