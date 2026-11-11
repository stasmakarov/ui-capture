package io.uitrainer.platform.view;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class UiBusinessExceptionHandlerTest {

    @Test
    void foreignKeyOnUserIsDeleteBlocked() {
        SQLException sql = new SQLException(
                "ERROR: update or delete on table \"uic_user\" violates foreign key constraint \"fk_uic_course_on_owner\"",
                "23503");
        Throwable wrapped = new DataIntegrityViolationException("could not execute statement", sql);
        assertThat(UiBusinessExceptionHandler.userReferenced(wrapped)).isTrue();
        assertThat(UiBusinessExceptionHandler.uniqueViolation(wrapped)).isFalse();
    }

    @Test
    void duplicateLoginIsNotDeleteBlocked() {
        SQLException sql = new SQLException(
                "ERROR: duplicate key value violates unique constraint \"idx_uic_user_on_username\" Detail: Key (username)=(trainer) already exists.",
                "23505");
        Throwable wrapped = new DataIntegrityViolationException("could not execute statement [23505]", sql);
        assertThat(UiBusinessExceptionHandler.userReferenced(wrapped)).isFalse();
        assertThat(UiBusinessExceptionHandler.uniqueViolation(wrapped)).isTrue();
    }
}
