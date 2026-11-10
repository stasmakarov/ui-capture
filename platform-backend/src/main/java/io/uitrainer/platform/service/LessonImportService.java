package io.uitrainer.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import io.jmix.core.DataManager;
import io.jmix.core.FileRef;
import io.jmix.core.FileStorage;
import io.jmix.core.FileStorageLocator;
import io.jmix.core.SaveContext;
import io.jmix.core.TimeSource;
import io.uitrainer.platform.entity.*;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Приём .lesson.zip: проверка пакета, файлы — в FileStorage, lesson.json — в JSONB новой неизменяемой LessonVersion.
 * Первый приём создаёт Lesson в статусе «Черновик», повторный — новую версию и возвращает урок в черновик.
 */
@Service
public class LessonImportService {

    private final DataManager dataManager;
    private final LessonPackageReader reader;
    private final FileStorageLocator fileStorageLocator;
    private final CurrentUserAccess access;
    private final CourseAccessService courseAccess;
    private final TimeSource timeSource;

    public LessonImportService(DataManager dataManager, LessonPackageReader reader, FileStorageLocator fileStorageLocator,
                               CurrentUserAccess access, CourseAccessService courseAccess, TimeSource timeSource) {
        this.dataManager = dataManager;
        this.reader = reader;
        this.fileStorageLocator = fileStorageLocator;
        this.access = access;
        this.courseAccess = courseAccess;
        this.timeSource = timeSource;
    }

    /** Импорт от имени текущего пользователя (REST оборачивает вызов в SystemAuthenticator.runWithUser). */
    @Transactional
    public ImportResult importPackage(byte[] archive, @Nullable UUID courseId, @Nullable String studioVersion) {
        if (!access.isTrainer() && !access.isAdmin()) {
            throw new AccessException("Загружать уроки могут тренеры и администраторы");
        }
        LessonPackage pkg = reader.read(archive);
        String username = access.username();
        User user = dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username = :u").parameter("u", username).one();

        Lesson lesson = dataManager.unconstrained().load(Lesson.class)
                .query("select l from uic_Lesson l where l.externalId = :id").parameter("id", pkg.lessonId())
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("author", "_base").add("course", "_base"))
                .optional().orElse(null);
        boolean newLesson = lesson == null;
        if (!newLesson && !mayChangeLesson(lesson, username)) {
            throw new AccessException("Урок «" + lesson.getTitle() + "» принадлежит другому автору (" + lesson.getAuthor().getUsername() + ")");
        }
        SaveContext save = new SaveContext();
        LessonStatus from = null;
        if (newLesson) {
            lesson = dataManager.create(Lesson.class);
            lesson.setExternalId(pkg.lessonId());
            lesson.setAuthor(user);
        } else {
            from = lesson.getStatus();
        }
        lesson.setTitle(pkg.title());
        String sourceSite = pkg.lesson().path("sourceSite").asText("");
        lesson.setSourceSite(sourceSite.isEmpty() ? null : sourceSite);
        lesson.setStatus(LessonStatus.DRAFT);
        if (courseId != null) {
            Course course = dataManager.unconstrained().load(Course.class).id(courseId)
                    .fetchPlan(fp -> fp.addFetchPlan("_base").add("owner", "_base"))
                    .optional()
                    .orElseThrow(() -> new PackageException("Курс " + courseId + " не найден"));
            if (!courseAccess.canEdit(course)) {
                throw new AccessException("Курс «" + course.getTitle() + "» недоступен для записи");
            }
            lesson.setCourse(course);
        }
        if (newLesson) {
            lesson.setSortOrder(nextLessonSortOrder(lesson.getCourse()));
        }

        int nextNo = 1;
        if (!newLesson) {
            Integer max = dataManager.unconstrained().loadValue(
                            "select max(v.versionNo) from uic_LessonVersion v where v.lesson = :l", Integer.class)
                    .parameter("l", lesson).one();
            nextNo = max == null ? 1 : max + 1;
        }

        FileStorage storage = fileStorageLocator.getDefault();
        String prefix = pkg.lessonId() + "-v" + nextNo + "-";
        LessonVersion version = dataManager.create(LessonVersion.class);
        version.setLesson(lesson);
        version.setVersionNo(nextNo);
        version.setTitle(pkg.title());
        version.setSchemaVersion(pkg.lesson().path("schemaVersion").isInt() ? pkg.lesson().path("schemaVersion").asInt() : null);
        version.setContent(pkg.lessonJson());
        version.setManifest(pkg.manifestJson());
        version.setContentSha256(LessonPackageReader.sha256(pkg.files().get("lesson.json")));
        version.setPackageSha256(pkg.packageSha256());
        version.setStepCount(pkg.lesson().path("steps").size());
        version.setUploadedBy(username);
        version.setStudioVersion(studioVersion);
        version.setPackageFile(storage.saveStream(prefix + "package.lesson.zip", new ByteArrayInputStream(archive)));

        List<Object> children = new ArrayList<>();
        for (Map.Entry<String, byte[]> file : pkg.files().entrySet()) {
            FileRef ref = storage.saveStream(prefix + file.getKey().replace('/', '_'), new ByteArrayInputStream(file.getValue()));
            if (file.getKey().equals("trainer.html")) version.setTrainerHtml(ref);
            LessonAsset asset = dataManager.create(LessonAsset.class);
            asset.setLessonVersion(version);
            asset.setPath(file.getKey());
            asset.setSha256(LessonPackageReader.sha256(file.getValue()));
            asset.setBytes((long) file.getValue().length);
            asset.setFile(ref);
            children.add(asset);
        }
        int index = 0;
        for (JsonNode step : pkg.lesson().path("steps")) {
            StepRef ref = dataManager.create(StepRef.class);
            ref.setLessonVersion(version);
            ref.setIndex(index++);
            ref.setStepId(step.path("id").asText("step-" + index));
            ref.setAction(step.path("action").asText(null));
            String instruction = step.path("instruction").asText("");
            ref.setInstructionShort(instruction.length() > 500 ? instruction.substring(0, 497) + "…" : instruction);
            children.add(ref);
        }
        LifecycleTransition transition = dataManager.create(LifecycleTransition.class);
        transition.setLesson(lesson);
        transition.setLessonVersion(version);
        transition.setFromStatus(from);
        transition.setToStatus(LessonStatus.DRAFT);
        transition.setActor(username);
        transition.setAt(now());
        transition.setComment(newLesson ? "Урок загружен из студии, версия 1" : "Загружена версия " + nextNo);

        save.saving(lesson, version, transition);
        children.forEach(save::saving);
        dataManager.unconstrained().save(save);
        // ссылку на текущую версию ставим вторым шагом: Lesson и LessonVersion ссылаются друг на друга
        Lesson saved = dataManager.unconstrained().load(Lesson.class).id(lesson.getId()).one();
        saved.setCurrentVersion(dataManager.unconstrained().getReference(LessonVersion.class, version.getId()));
        saved = dataManager.unconstrained().save(saved);
        LessonVersion savedVersion = dataManager.unconstrained().load(LessonVersion.class).id(version.getId()).one();
        return new ImportResult(saved, savedVersion, newLesson);
    }

    private boolean mayChangeLesson(Lesson lesson, String username) {
        if (access.isAdmin()) {
            return true;
        }
        if (lesson.getAuthor() != null && username.equals(lesson.getAuthor().getUsername())) {
            return true;
        }
        return lesson.getCourse() != null && courseAccess.canEdit(lesson.getCourse());
    }

    private int nextLessonSortOrder(Course course) {
        if (course == null || course.getId() == null) {
            return 1;
        }
        Integer max = dataManager.unconstrained().loadValue(
                        "select max(l.sortOrder) from uic_Lesson l where l.course.id = :c", Integer.class)
                .parameter("c", course.getId())
                .optional()
                .orElse(null);
        return max == null ? 1 : max + 1;
    }

    private OffsetDateTime now() {
        return timeSource.now().toOffsetDateTime();
    }
}
