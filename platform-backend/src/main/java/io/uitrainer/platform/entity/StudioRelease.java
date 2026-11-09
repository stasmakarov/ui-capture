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
@Table(name = "UIC_STUDIO_RELEASE", uniqueConstraints = {
        @UniqueConstraint(name = "IDX_UIC_STUDIO_RELEASE_PLATFORM", columnNames = {"PLATFORM_"})
})
@Entity(name = "uic_StudioRelease")
public class StudioRelease {

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
    @Column(name = "VERSION_NAME", nullable = false, length = 64)
    private String versionName;

    @Column(name = "PLATFORM_", nullable = false, length = 16)
    private String platform;

    @Column(name = "FILE_NAME", nullable = false, length = 255)
    private String fileName;

    @Column(name = "SHA512", nullable = false, length = 200)
    private String sha512;

    @Column(name = "SIZE_", nullable = false)
    private Long size;

    @Column(name = "EXE", nullable = false, length = 1024)
    private FileRef exe;

    @Column(name = "LATEST_YML", nullable = false, length = 1024)
    private FileRef latestYml;

    @Column(name = "UPLOADED_AT", nullable = false)
    private OffsetDateTime uploadedAt;

    @Column(name = "UPLOADED_BY", nullable = false)
    private String uploadedBy;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getSha512() { return sha512; }
    public void setSha512(String sha512) { this.sha512 = sha512; }
    public Long getSize() { return size; }
    public void setSize(Long size) { this.size = size; }
    public FileRef getExe() { return exe; }
    public void setExe(FileRef exe) { this.exe = exe; }
    public FileRef getLatestYml() { return latestYml; }
    public void setLatestYml(FileRef latestYml) { this.latestYml = latestYml; }
    public OffsetDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(OffsetDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}
