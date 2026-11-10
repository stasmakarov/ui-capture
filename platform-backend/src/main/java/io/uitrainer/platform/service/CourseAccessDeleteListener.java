package io.uitrainer.platform.service;

import io.jmix.core.security.AccessDeniedException;
import io.jmix.data.listener.BeforeDeleteEntityListener;
import io.uitrainer.platform.entity.CourseCoauthor;
import io.uitrainer.platform.entity.CourseSharedWith;
import org.springframework.stereotype.Component;

@Component("uic_CourseCoauthorDeleteListener")
class CourseCoauthorDeleteListener implements BeforeDeleteEntityListener<CourseCoauthor> {

    private final CourseAccessService courseAccess;

    CourseCoauthorDeleteListener(CourseAccessService courseAccess) {
        this.courseAccess = courseAccess;
    }

    @Override
    public void onBeforeDelete(CourseCoauthor row) {
        if (!courseAccess.canManageAccess(row.getCourse())) {
            throw new AccessDeniedException("entity", "uic_CourseCoauthor", "delete");
        }
    }
}

@Component("uic_CourseSharedWithDeleteListener")
class CourseSharedWithDeleteListener implements BeforeDeleteEntityListener<CourseSharedWith> {

    private final CourseAccessService courseAccess;

    CourseSharedWithDeleteListener(CourseAccessService courseAccess) {
        this.courseAccess = courseAccess;
    }

    @Override
    public void onBeforeDelete(CourseSharedWith row) {
        if (!courseAccess.canManageAccess(row.getCourse())) {
            throw new AccessDeniedException("entity", "uic_CourseSharedWith", "delete");
        }
    }
}
