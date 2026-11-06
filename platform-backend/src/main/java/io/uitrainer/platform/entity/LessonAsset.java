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
@Table(name = "UIC_LESSON_ASSET")
@Entity(name = "uic_LessonAsset")
public class LessonAsset {
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

    @InstanceName
    @Column(name = "PATH", nullable = false, length = 512)
    private String path;

    @Column(name = "SHA256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "BYTES", nullable = false)
    private Long bytes;

    @Column(name = "FILE_", nullable = false, length = 1024)
    private FileRef file;

    public LessonVersion getLessonVersion() { return lessonVersion; }
    public void setLessonVersion(LessonVersion lessonVersion) { this.lessonVersion = lessonVersion; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public Long getBytes() { return bytes; }
    public void setBytes(Long bytes) { this.bytes = bytes; }
    public FileRef getFile() { return file; }
    public void setFile(FileRef file) { this.file = file; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
}
