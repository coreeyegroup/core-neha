package in.coreeye.coreneha.iam;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import in.coreeye.coreneha.audit.AuditEntry;
import in.coreeye.coreneha.audit.AuditLog;
import in.coreeye.coreneha.audit.AuditOutcome;
import in.coreeye.coreneha.kernel.Actor;
import in.coreeye.coreneha.kernel.CurrentActor;
import in.coreeye.coreneha.kernel.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the authenticated identity to an active core-neha user on every
 * request, sets the tenant for row-level security and the request id for logs.
 * A disabled or unknown user is logged out immediately (IAM-04).
 */
@Component
class ActorContext extends OncePerRequestFilter implements CurrentActor {

    static final String ACTOR_ATTRIBUTE = "coreneha.actor";

    private final JdbcClient jdbc;
    private final AuditLog audit;

    ActorContext(JdbcClient jdbc, AuditLog audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Override
    public Optional<Actor> find() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((Actor) attrs.getAttribute(ACTOR_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader("X-Request-Id");
        MDC.put("request_id", requestId == null || requestId.isBlank() || requestId.length() > 64
                ? UUID.randomUUID().toString() : requestId);
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
                Optional<Actor> actor = resolve(auth.getName());
                if (actor.isEmpty()) {
                    refuse(request, response, auth.getName());
                    return;
                }
                TenantContext.set(actor.get().tenantId());
                request.setAttribute(ACTOR_ATTRIBUTE, actor.get());
                MDC.put("user_id", actor.get().userId().toString());
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove("request_id");
            MDC.remove("user_id");
        }
    }

    /** Subject -> active user. Login identities are looked up before the tenant is known (no RLS on that table). */
    Optional<Actor> resolve(String subject) {
        Optional<UUID[]> identity = jdbc.sql("select user_id, tenant_id from iam.login_identity where subject = :s")
                .param("s", subject)
                .query((rs, i) -> new UUID[] {rs.getObject("user_id", UUID.class), rs.getObject("tenant_id", UUID.class)})
                .optional();
        if (identity.isEmpty()) {
            return Optional.empty();
        }
        UUID userId = identity.get()[0];
        UUID tenantId = identity.get()[1];
        return TenantContext.callAs(tenantId, () -> jdbc.sql("""
                        select u.id as user_id, u.tenant_id, u.person_id, u.username, u.access_version
                        from iam.user_account u join org.tenant t on t.id = u.tenant_id
                        where u.id = :id and u.status = 'ACTIVE' and t.status = 'ACTIVE'
                        """).param("id", userId).query(Actor.class).optional());
    }

    private void refuse(HttpServletRequest request, HttpServletResponse response, String subject) throws IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        audit.recordIndependently(new AuditEntry(null, null, "subject:" + subject, "iam.session.refused", "login_identity",
                subject, null, AuditOutcome.DENIED, "unknown or disabled account", java.time.Instant.now(), Map.of()));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"status\":401,\"title\":\"Login required\",\"code\":\"session.ended\"}");
    }
}
