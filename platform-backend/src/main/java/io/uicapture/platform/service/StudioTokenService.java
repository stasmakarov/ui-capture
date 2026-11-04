package io.uicapture.platform.service;

import io.jmix.core.DataManager;
import io.jmix.core.TimeSource;
import io.uicapture.platform.entity.Course;
import io.uicapture.platform.entity.StudioTicket;
import io.uicapture.platform.entity.StudioToken;
import io.uicapture.platform.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Персональные токены студии (в БД только SHA-256) и одноразовые короткоживущие тикеты
 * для запуска студии кнопкой «Записать в студии».
 */
@Service
public class StudioTokenService {

    public static final String TOKEN_PREFIX = "uicst_";
    public static final String TICKET_PREFIX = "uictk_";

    private final DataManager dataManager;
    private final CurrentUserAccess access;
    private final TimeSource timeSource;
    private final SecureRandom random = new SecureRandom();
    private final Duration ticketTtl;
    private final Duration exchangedTokenTtl;

    public StudioTokenService(DataManager dataManager, CurrentUserAccess access, TimeSource timeSource,
                              @Value("${uicapture.studio.ticket-ttl:PT5M}") Duration ticketTtl,
                              @Value("${uicapture.studio.exchanged-token-ttl:P90D}") Duration exchangedTokenTtl) {
        this.dataManager = dataManager;
        this.access = access;
        this.timeSource = timeSource;
        this.ticketTtl = ticketTtl;
        this.exchangedTokenTtl = exchangedTokenTtl;
    }

    public record IssuedToken(StudioToken token, String plainText) {
    }

    public record Exchange(String token, String username, @Nullable UUID courseId, @Nullable String courseTitle, OffsetDateTime expiresAt) {
    }

    /** Выпустить токен текущему пользователю. Открытый текст возвращается один раз. */
    @Transactional
    public IssuedToken issueToken(String name, @Nullable Duration ttl) {
        return issueFor(currentUser(), name, ttl);
    }

    private IssuedToken issueFor(User owner, String name, @Nullable Duration ttl) {
        String plain = TOKEN_PREFIX + randomPart();
        StudioToken token = dataManager.create(StudioToken.class);
        token.setOwner(owner);
        token.setName(name == null || name.isBlank() ? "Студия" : name.trim());
        token.setTokenHash(LessonPackageReader.sha256(plain.getBytes(StandardCharsets.UTF_8)));
        token.setPrefix(plain.substring(0, 12));
        token.setExpiresAt(ttl == null ? null : now().plus(ttl));
        token.setRevoked(false);
        return new IssuedToken(dataManager.unconstrained().save(token), plain);
    }

    @Transactional
    public void revoke(UUID tokenId) {
        StudioToken token = dataManager.unconstrained().load(StudioToken.class).id(tokenId)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("owner", "_base")).one();
        if (!token.getOwner().getUsername().equals(access.username()) && !access.isAdmin()) {
            throw new AccessException("Отозвать можно только свой токен");
        }
        token.setRevoked(true);
        token.setRevokedAt(now());
        dataManager.unconstrained().save(token);
    }

    /** Проверка Bearer-токена: имя владельца или пусто. Вызывается без аутентификации. */
    @Transactional
    public Optional<String> authenticate(@Nullable String plain) {
        if (plain == null || !plain.startsWith(TOKEN_PREFIX)) return Optional.empty();
        String hash = LessonPackageReader.sha256(plain.getBytes(StandardCharsets.UTF_8));
        Optional<StudioToken> found = dataManager.unconstrained().load(StudioToken.class)
                .query("select t from uic_StudioToken t where t.tokenHash = :h").parameter("h", hash)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("owner", "_base")).optional();
        if (found.isEmpty()) return Optional.empty();
        StudioToken token = found.get();
        if (Boolean.TRUE.equals(token.getRevoked())) return Optional.empty();
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(now())) return Optional.empty();
        if (!Boolean.TRUE.equals(token.getOwner().getActive())) return Optional.empty();
        token.setLastUsedAt(now());
        dataManager.unconstrained().save(token);
        return Optional.of(token.getOwner().getUsername());
    }

    /** Одноразовый тикет для ссылки uicapture://new — живёт минуты. */
    @Transactional
    public String issueTicket(@Nullable UUID courseId) {
        String plain = TICKET_PREFIX + randomPart();
        StudioTicket ticket = dataManager.create(StudioTicket.class);
        ticket.setOwner(currentUser());
        if (courseId != null) ticket.setCourse(dataManager.unconstrained().load(Course.class).id(courseId).one());
        ticket.setTicketHash(LessonPackageReader.sha256(plain.getBytes(StandardCharsets.UTF_8)));
        ticket.setExpiresAt(now().plus(ticketTtl));
        dataManager.unconstrained().save(ticket);
        return plain;
    }

    /** Обмен тикета на токен студии. Второй обмен и просроченный тикет — отказ. */
    @Transactional
    public Exchange exchangeTicket(@Nullable String plainTicket) {
        if (plainTicket == null || !plainTicket.startsWith(TICKET_PREFIX)) throw new AccessException("Неверный тикет");
        String hash = LessonPackageReader.sha256(plainTicket.getBytes(StandardCharsets.UTF_8));
        StudioTicket ticket = dataManager.unconstrained().load(StudioTicket.class)
                .query("select t from uic_StudioTicket t where t.ticketHash = :h").parameter("h", hash)
                .fetchPlan(fp -> fp.addFetchPlan("_base").add("owner", "_base").add("course", "_base"))
                .optional().orElseThrow(() -> new AccessException("Тикет не найден"));
        if (ticket.getUsedAt() != null) throw new AccessException("Тикет уже использован. Нажмите «Записать в студии» ещё раз");
        if (ticket.getExpiresAt().isBefore(now())) throw new AccessException("Тикет просрочен. Нажмите «Записать в студии» ещё раз");
        ticket.setUsedAt(now());
        dataManager.unconstrained().save(ticket);
        IssuedToken issued = issueFor(ticket.getOwner(), "Студия (по ссылке из админки)", exchangedTokenTtl);
        Course course = ticket.getCourse();
        return new Exchange(issued.plainText(), ticket.getOwner().getUsername(),
                course == null ? null : course.getId(), course == null ? null : course.getTitle(),
                issued.token().getExpiresAt());
    }

    private User currentUser() {
        return dataManager.unconstrained().load(User.class)
                .query("select u from User u where u.username = :u").parameter("u", access.username()).one();
    }

    private String randomPart() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private OffsetDateTime now() {
        return timeSource.now().toOffsetDateTime();
    }
}
