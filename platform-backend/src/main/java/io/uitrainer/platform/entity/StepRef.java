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
@Table(name = "UIC_STEP_REF")
@Entity(name = "uic_StepRef")
public class StepRef {
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
    @JoinColumn(name = "LESSON_VERSION_ID", nullable = false)
    private LessonVersion lessonVersion;

    @Column(name = "STEP_ID", nullable = false)
    private String stepId;

    @Column(name = "INDEX_", nullable = false)
    private Integer index;

    @Column(name = "ACTION_")
    private String action;

    @InstanceName
    @Column(name = "INSTRUCTION_SHORT", length = 500)
    private String instructionShort;

    public LessonVersion getLessonVersion() { return lessonVersion; }
    public void setLessonVersion(LessonVersion lessonVersion) { this.lessonVersion = lessonVersion; }
    public String getStepId() { return stepId; }
    public void setStepId(String stepId) { this.stepId = stepId; }
    public Integer getIndex() { return index; }
    public void setIndex(Integer index) { this.index = index; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getInstructionShort() { return instructionShort; }
    public void setInstructionShort(String instructionShort) { this.instructionShort = instructionShort; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
}
