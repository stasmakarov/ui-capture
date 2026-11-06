package io.uitrainer.platform.dev;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Профиль {@code dev}: до инициализации DataSource/Liquibase создаёт роль и БД приложения
 * через admin-подключение (по умолчанию jmix/jmix к postgres), если их ещё нет.
 * При ошибке admin-подключения пишет предупреждение и продолжает старт.
 * <p>
 * Регистрация: {@code META-INF/spring.factories} (Spring Boot 3.5 всё ещё читает EPPs оттуда)
 * и дублирующий файл {@code META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor}.
 */
public class DevDatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Pattern JDBC_URL = Pattern.compile(
            "^jdbc:postgresql://([^/:?]+)(?::(\\d+))?/([^?]+)(?:\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    private static final String DEFAULT_ADMIN_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DEFAULT_ADMIN_USER = "jmix";
    private static final String DEFAULT_ADMIN_PASSWORD = "jmix";

    /** После {@code ConfigDataEnvironmentPostProcessor}, чтобы были видны profile-свойства. */
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 20;

    private final Log log;

    public DevDatabaseEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DevDatabaseEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of("dev"))) {
            return;
        }

        String mainUrl = environment.getProperty("main.datasource.url");
        String appUser = environment.getProperty("main.datasource.username", "uicapture");
        String appPassword = environment.getProperty("main.datasource.password", "uicapture");

        if (!StringUtils.hasText(mainUrl)) {
            log.warn("Dev DB bootstrap skipped: main.datasource.url is empty");
            return;
        }

        Matcher matcher = JDBC_URL.matcher(mainUrl.trim());
        if (!matcher.matches()) {
            log.warn("Dev DB bootstrap skipped: cannot parse PostgreSQL JDBC URL: " + mainUrl);
            return;
        }

        String host = matcher.group(1);
        String port = matcher.group(2) != null ? matcher.group(2) : "5432";
        String dbName = matcher.group(3);

        if (!isSafeIdent(appUser) || !isSafeIdent(dbName)) {
            log.warn("Dev DB bootstrap skipped: unsafe role/database identifier");
            return;
        }

        String adminUrl = property(environment, "uitrainer.dev.db-admin.url", "uicapture.dev.db-admin.url", DEFAULT_ADMIN_URL);
        String adminUser = property(environment, "uitrainer.dev.db-admin.username", "uicapture.dev.db-admin.username", DEFAULT_ADMIN_USER);
        String adminPassword = property(environment, "uitrainer.dev.db-admin.password", "uicapture.dev.db-admin.password", DEFAULT_ADMIN_PASSWORD);

        log.info("Dev DB bootstrap: ensuring role/database via " + adminUser + " @ " + adminUrl
                + " for " + appUser + "/" + dbName + " (main host " + host + ":" + port + ")");

        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            log.warn("Dev DB bootstrap skipped: PostgreSQL JDBC driver not on classpath");
            return;
        }

        try (Connection connection = DriverManager.getConnection(adminUrl, adminUser, adminPassword);
             Statement statement = connection.createStatement()) {
            ensureRole(statement, appUser, appPassword);
            ensureDatabase(statement, dbName, appUser);
            // Тестовая БД для ./gradlew test (application-test.properties)
            if ("uicapture".equals(appUser) && !"uicapture_test".equals(dbName)) {
                ensureDatabase(statement, "uicapture_test", appUser);
            }
        } catch (SQLException e) {
            log.warn("Dev DB bootstrap: cannot connect or create with admin credentials ("
                    + adminUser + " @ " + adminUrl + "): " + e.getMessage()
                    + ". Continuing startup; ensure the database exists or fix uitrainer.dev.db-admin.*");
        }
    }

    private void ensureRole(Statement statement, String role, String password) throws SQLException {
        if (exists(statement, "SELECT 1 FROM pg_roles WHERE rolname = '" + escapeLiteral(role) + "'")) {
            log.info("Dev DB bootstrap: role already exists: " + role);
            return;
        }
        statement.execute("CREATE ROLE " + quoteIdent(role)
                + " WITH LOGIN PASSWORD '" + escapeLiteral(password) + "'");
        log.info("Dev DB bootstrap: created role " + role);
    }

    private void ensureDatabase(Statement statement, String dbName, String owner) throws SQLException {
        if (exists(statement, "SELECT 1 FROM pg_database WHERE datname = '" + escapeLiteral(dbName) + "'")) {
            log.info("Dev DB bootstrap: database already exists: " + dbName);
            return;
        }
        statement.execute("CREATE DATABASE " + quoteIdent(dbName) + " OWNER " + quoteIdent(owner));
        log.info("Dev DB bootstrap: created database " + dbName + " OWNER " + owner);
    }

    private static boolean exists(Statement statement, String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            return rs.next();
        }
    }

    private static String property(ConfigurableEnvironment environment, String key, String legacyKey, String defaultValue) {
        String primary = environment.getProperty(key);
        if (StringUtils.hasText(primary)) return primary;
        String legacy = environment.getProperty(legacyKey);
        if (StringUtils.hasText(legacy)) return legacy;
        return defaultValue;
    }

    private static boolean isSafeIdent(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_]*");
    }

    private static String quoteIdent(String ident) {
        return "\"" + ident.replace("\"", "\"\"") + "\"";
    }

    private static String escapeLiteral(String value) {
        return value.replace("'", "''");
    }
}
