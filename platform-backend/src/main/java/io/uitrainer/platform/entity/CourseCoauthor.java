package io.uitrainer.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

@JmixEntity
@Table(name = "UIC_COURSE_COAUTHOR", indexes = {
        @Index(name = "IDX_UIC_COURSE_COAUTHOR_COURSE", columnList = "COURSE_ID"),
        @Index(name = "IDX_UIC_COURSE_COAUTHOR_TRAINER", columnList = "TRAINER_ID"),
        @Index(name = "UQ_UIC_COURSE_COAUTHOR", columnList = "COURSE_ID, TRAINER_ID", unique = true)
})
@Entity(name = "uic_CourseCoauthor")
public class CourseCoauthor {
    @JmixGeneratedValue
    @Column(name = "ID", nullable = false)
    @Id
    private UUID id;

    @Version
    @Column(name = "VERSION", nullable = false)
    private Integer version;

    @OnDeleteInverse(DeletePolicy.CASCADE)
    @JoinColumn(name = "COURSE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    @JoinColumn(name = "TRAINER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User trainer;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public User getTrainer() { return trainer; }
    public void setTrainer(User trainer) { this.trainer = trainer; }

    @InstanceName
    @DependsOnProperties({"trainer"})
    public String getInstanceName() {
        return trainer == null ? "" : trainer.getDisplayName();
    }
}
