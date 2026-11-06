package io.uitrainer.platform.entity;

import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UIC_COURSE_REVISION")
@Entity(name = "uic_CourseRevision")
public class CourseRevision {
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "COURSE_ID", nullable = false)
    private Course course;

    @InstanceName
    @Column(name = "REVISION_NO", nullable = false)
    private Integer revisionNo;

    /** Статусы ревизии курса появятся в M3; пока строка DRAFT. */
    @Column(name = "STATUS", nullable = false)
    private String status = "DRAFT";

    @Lob
    @Column(name = "CHANGE_NOTE")
    private String changeNote;

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Integer getRevisionNo() { return revisionNo; }
    public void setRevisionNo(Integer revisionNo) { this.revisionNo = revisionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChangeNote() { return changeNote; }
    public void setChangeNote(String changeNote) { this.changeNote = changeNote; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
}
