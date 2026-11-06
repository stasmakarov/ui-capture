package io.uicapture.platform.entity;

import io.jmix.core.DeletePolicy;
import io.jmix.core.FileRef;
import io.jmix.core.entity.annotation.JmixGeneratedValue;
import io.jmix.core.entity.annotation.OnDeleteInverse;
import io.jmix.core.metamodel.annotation.InstanceName;
import io.jmix.core.metamodel.annotation.JmixEntity;
import jakarta.persistence.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;

import java.time.OffsetDateTime;
import java.util.UUID;

@JmixEntity
@Table(name = "UIC_LESSON_VERSION", uniqueConstraints = {@UniqueConstraint(name = "IDX_UIC_LESSON_VERSION_NO", columnNames = {"LESSON_ID", "VERSION_NO"})})
@Entity(name = "uic_LessonVersion")
public class LessonVersion {
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

    @OnDeleteInverse(DeletePolicy.DENY)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "LESSON_ID", nullable = false)
    private Lesson lesson;

    @InstanceName
    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Column(name = "SCHEMA_VERSION")
    private Integer schemaVersion;

    @Column(name = "TITLE")
    private String title;

    /** lesson.json как есть, PostgreSQL JSONB */
    @Lob
    @Column(name = "CONTENT", columnDefinition = "jsonb", nullable = false)
    private String content;

    /** manifest.json пакета, JSONB */
    @Lob
    @Column(name = "MANIFEST", columnDefinition = "jsonb")
    private String manifest;

    @Column(name = "CONTENT_SHA256", length = 64)
    private String contentSha256;

    @Column(name = "PACKAGE_SHA256", length = 64)
    private String packageSha256;

    @Column(name = "STEP_COUNT")
    private Integer stepCount;

    @Column(name = "UPLOADED_BY")
    private String uploadedBy;

    @Column(name = "PACKAGE_FILE", length = 1024)
    private FileRef packageFile;

    @Column(name = "TRAINER_HTML", length = 1024)
    private FileRef trainerHtml;

    @Column(name = "STUDIO_VERSION")
    private String studioVersion;

    public Lesson getLesson() { return lesson; }
    public void setLesson(Lesson lesson) { this.lesson = lesson; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public Integer getSchemaVersion() { return schemaVersion; }
    public void setSchemaVersion(Integer schemaVersion) { this.schemaVersion = schemaVersion; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getManifest() { return manifest; }
    public void setManifest(String manifest) { this.manifest = manifest; }
    public String getContentSha256() { return contentSha256; }
    public void setContentSha256(String contentSha256) { this.contentSha256 = contentSha256; }
    public String getPackageSha256() { return packageSha256; }
    public void setPackageSha256(String packageSha256) { this.packageSha256 = packageSha256; }
    public Integer getStepCount() { return stepCount; }
    public void setStepCount(Integer stepCount) { this.stepCount = stepCount; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public FileRef getPackageFile() { return packageFile; }
    public void setPackageFile(FileRef packageFile) { this.packageFile = packageFile; }
    public FileRef getTrainerHtml() { return trainerHtml; }
    public void setTrainerHtml(FileRef trainerHtml) { this.trainerHtml = trainerHtml; }
    public String getStudioVersion() { return studioVersion; }
    public void setStudioVersion(String studioVersion) { this.studioVersion = studioVersion; }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime createdDate) { this.createdDate = createdDate; }
}
