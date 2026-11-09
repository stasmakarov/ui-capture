package io.uitrainer.platform.entity;

import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.util.UUID;

@JmixEntity
@Table(name = "UIC_GROUP_MEMBER", indexes = {
        @Index(name = "IDX_UIC_GROUP_MEMBER_GROUP", columnList = "GROUP_ID"),
        @Index(name = "IDX_UIC_GROUP_MEMBER_STUDENT", columnList = "STUDENT_ID"),
        @Index(name = "UQ_UIC_GROUP_MEMBER", columnList = "GROUP_ID, STUDENT_ID", unique = true)
})
@Entity(name = "uic_GroupMember")
public class GroupMember {
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

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public StudyGroup getGroup() { return group; }
    public void setGroup(StudyGroup group) { this.group = group; }
    public User getStudent() { return student; }
    public void setStudent(User student) { this.student = student; }

    @InstanceName
    @DependsOnProperties({"student"})
    public String getInstanceName() {
        return student == null ? "" : student.getDisplayName();
    }
}
