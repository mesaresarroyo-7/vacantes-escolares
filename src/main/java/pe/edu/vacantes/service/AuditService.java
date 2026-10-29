package pe.edu.vacantes.service;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pe.edu.vacantes.domain.AuditEvent;
import pe.edu.vacantes.repository.AuditEventRepository;

@Service
public class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private static final Set<String> LEVELS = Set.of("INFO", "WARN", "ERROR");
    private final AuditEventRepository events;
    private final TransactionTemplate separateTransaction;

    public AuditService(AuditEventRepository events, PlatformTransactionManager transactions) {
        this.events = events;
        separateTransaction = new TransactionTemplate(transactions);
        separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Report success only after the business transaction actually commits. */
    public void recordAfterCommit(String actor, String action, String target, String detail) {
        String origin = requestIp();
        Runnable save = () -> {
            try {
                separateTransaction.executeWithoutResult(transaction ->
                        record(actor, action, target, detail, "INFO", origin));
            } catch (RuntimeException failure) {
                LOG.error("No se pudo guardar el resultado de auditoría accion={}. Tipo={}",
                        safe(action, 50), failure.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { save.run(); }
            });
        } else {
            save.run();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String target, String detail) {
        record(actor, action, target, detail, "INFO", requestIp());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String target, String detail,
                       String level, String originIp) {
        AuditEvent event = new AuditEvent();
        event.setActor(safe(actor == null ? "anonimo" : actor, 190));
        event.setAction(safe(action == null ? "EVENTO" : action, 50));
        event.setTarget(safe(target, 100));
        event.setDetail(safe(detail, 250));
        String selected = level == null ? "INFO" : level.toUpperCase(Locale.ROOT);
        event.setLevel(LEVELS.contains(selected) ? selected : "INFO");
        event.setOriginIp(originIp != null && originIp.matches("[0-9a-fA-F:.%]{1,64}")
                ? originIp : "no_disponible");
        event.setCreatedAt(Instant.now());
        events.saveAndFlush(event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { writeLog(event); }
            });
        } else {
            writeLog(event);
        }
    }

    private void writeLog(AuditEvent event) {
        // Every field comes from the same sanitized record used by MySQL.
        String message = "AUDITORIA actor={} accion={} referencia={} nivel={} origen={} detalle={}";
        Object[] values = {event.getActor(), event.getAction(), event.getTarget(),
                event.getLevel(), event.getOriginIp(), event.getDetail()};
        switch (event.getLevel()) {
            case "WARN" -> LOG.warn(message, values);
            case "ERROR" -> LOG.error(message, values);
            default -> LOG.info(message, values);
        }
    }

    private String requestIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            // The application is local; do not trust client-supplied forwarding headers.
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }

    private String redact(String value) {
        if (value == null) return null;
        return value.replaceAll("(?i)(password|contraseña|secret(?:_key)?|token|authorization)(\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|\\S+)", "$1$2[omitido]")
                .replaceAll("(?<![\\w-])[0-9]{8}(?![\\w-])", "[documento omitido]");
    }

    private String safe(String value, int maximum) {
        if (value == null) return null;
        String safe = redact(value).replaceAll("\\p{Cntrl}", " ").trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
