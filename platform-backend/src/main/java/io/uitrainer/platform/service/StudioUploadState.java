package io.uitrainer.platform.service;

import org.springframework.lang.Nullable;

/** Можно ли нажать «Загрузить релиз» и почему нет. Без UI. */
public final class StudioUploadState {

    private StudioUploadState() {
    }

    public record Decision(boolean enabled, String reason) {
    }

    public static Decision of(boolean hasExe, byte[] yml, @Nullable String currentServerVersion) {
        if (!hasExe || yml == null || yml.length == 0) {
            return new Decision(false, "Выберите оба файла");
        }
        String ver;
        try {
            ver = StudioReleaseService.parseLatestYml(yml).version();
        } catch (PackageException ex) {
            return new Decision(false, ex.getMessage() == null ? "latest.yml не разобран" : ex.getMessage());
        }
        if (currentServerVersion == null || currentServerVersion.isBlank()) {
            return new Decision(true, "Будет загружена версия " + ver);
        }
        int cmp;
        try {
            cmp = StudioReleaseService.compareVersions(ver, currentServerVersion);
        } catch (PackageException ex) {
            return new Decision(false, ex.getMessage() == null ? "Версия некорректна" : ex.getMessage());
        }
        if (cmp == 0) {
            return new Decision(false, "Версия " + ver + " уже загружена");
        }
        if (cmp < 0) {
            return new Decision(false, "Версия " + ver + " старше загруженной " + currentServerVersion);
        }
        return new Decision(true, "Будет загружена версия " + ver);
    }
}
