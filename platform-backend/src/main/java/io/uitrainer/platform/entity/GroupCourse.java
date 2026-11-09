package io.uitrainer.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.DependsOnProperties;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;

import java.util.UUID;

@JmixEntity
@Table(name = "UIC_GROUP_COURSE", indexes = {
        @Index(name = "IDX_UIC_GROUP_COURSE_GROUP", columnList = "GROUP_ID"),
        @Index(name = "IDX_UIC_GROUP_COURSE_COURSE", columnList = "COURSE_ID"),
        @Index(name = "UQ_UIC_GROUP_COURSE", columnList = "GROUP_ID, COURSE_ID", unique = true),
        @Index(name = "UQ_UIC_GROUP_COURSE_ORDER", columnList = "GROUP_ID, SORT_ORDER", unique = true)
})
@Entity(name = "uic_GroupCourse")
public class GroupCourse {
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

    @OnDeleteInverse(DeletePolicy.DENY)
    @JoinColumn(name = "COURSE_ID", nullable = false)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Course course;

    @Column(name = "SORT_ORDER", nullable = false)
    private Integer sortOrder = 0;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public StudyGroup getGroup() { return group; }
    public void setGroup(StudyGroup group) { this.group = group; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    @InstanceName
    @DependsOnProperties({"course", "sortOrder"})
    public String getInstanceName() {
        String title = course == null ? "" : course.getTitle();
        return sortOrder + ". " + title;
    }
}
