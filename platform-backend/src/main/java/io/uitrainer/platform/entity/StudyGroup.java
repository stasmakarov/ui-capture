package io.uitrainer.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDelete;
import io.jmix.core.metamodel.annotation.Composition;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@JmixEntity
@Table(name = "UIC_STUDY_GROUP", indexes = {
        @Index(name = "IDX_UIC_STUDY_GROUP_OWNER", columnList = "OWNER_ID")
})
@Entity(name = "uic_StudyGroup")
public class StudyGroup {
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

    @InstanceName
    @Column(name = "NAME", nullable = false)
    private String name;

    @JoinColumn(name = "OWNER_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User owner;

    @Column(name = "STATUS", nullable = false, length = 20)
    private String status = GroupStatus.CREATED.getId();

    @Column(name = "ACCESS_MODE", nullable = false, length = 20)
    private String accessMode = GroupAccessMode.ALL_AT_ONCE.getId();

    @Composition
    @OnDelete(DeletePolicy.CASCADE)
    @OrderBy("sortOrder")
    @OneToMany(mappedBy = "group")
    private List<GroupCourse> courses;

    @Composition
    @OnDelete(DeletePolicy.CASCADE)
    @OneToMany(mappedBy = "group")
    private List<GroupMember> members;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public User getOwner() { return owner; }
    public void setOwner(User owner) { this.owner = owner; }
    public GroupStatus getStatus() { return status == null ? null : GroupStatus.fromId(status); }
    public void setStatus(GroupStatus status) { this.status = status == null ? null : status.getId(); }
    public GroupAccessMode getAccessMode() { return accessMode == null ? null : GroupAccessMode.fromId(accessMode); }
    public void setAccessMode(GroupAccessMode accessMode) { this.accessMode = accessMode == null ? null : accessMode.getId(); }
    public List<GroupCourse> getCourses() { return courses; }
    public void setCourses(List<GroupCourse> courses) { this.courses = courses; }
    public List<GroupMember> getMembers() { return members; }
    public void setMembers(List<GroupMember> members) { this.members = members; }
}
