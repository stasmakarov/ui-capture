package io.uitrainer.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UIC_GROUP_COURSE_PROGRESS", indexes = {
        @Index(name = "IDX_UIC_GCP_GROUP", columnList = "GROUP_ID"),
        @Index(name = "IDX_UIC_GCP_STUDENT", columnList = "STUDENT_ID"),
        @Index(name = "IDX_UIC_GCP_COURSE", columnList = "COURSE_ID"),
        @Index(name = "UQ_UIC_GCP", columnList = "GROUP_ID, STUDENT_ID, COURSE_ID", unique = true)
})
@Entity(name = "uic_GroupCourseProgress")
public class GroupCourseProgress {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @JoinColumn(name = "GROUP_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private StudyGroup group;

    @JoinColumn(name = "STUDENT_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User student;

    @OnDeleteInverse(DeletePolicy.DENY)
    @JoinColumn(name = "COURSE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = CourseProgressStatus.NOT_STARTED.getId();

    @Column(name = "COMPLETED_AT")
    private OffsetDateTime completedAt;

    @Column(name = "PUBLISHED_LESSON_COUNT_AT_COMPLETION")
    private Integer publishedLessonCountAtCompletion;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public StudyGroup getGroup() { return group; }
    public void setGroup(StudyGroup group) { this.group = group; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public CourseProgressStatus getStatus() { return status == null ? null : CourseProgressStatus.fromId(status); }
    public void setStatus(CourseProgressStatus status) { this.status = status == null ? null : status.getId(); }
    public OffsetDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(OffsetDateTime completedAt) { this.completedAt = completedAt; }
    public Integer getPublishedLessonCountAtCompletion() { return publishedLessonCountAtCompletion; }
    public void setPublishedLessonCountAtCompletion(Integer publishedLessonCountAtCompletion) {
        this.publishedLessonCountAtCompletion = publishedLessonCountAtCompletion;
    }
}
