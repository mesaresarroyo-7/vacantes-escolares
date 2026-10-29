package pe.edu.vacantes.service;

import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.vacantes.domain.AuditEvent;
import pe.edu.vacantes.repository.AuditEventRepository;

@Service
public class AuditService {
    private final AuditEventRepository events;

    public AuditService(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String actor, String action, String target, String detail) {
        AuditEvent event = new AuditEvent();
        event.setActor(limit(actor == null ? "anonimo" : actor, 190));
        event.setAction(limit(action == null ? "EVENTO" : action, 50));
        event.setTarget(limit(target, 100));
        event.setDetail(limit(redact(detail), 250));
        event.setCreatedAt(Instant.now());
        events.saveAndFlush(event);
    }

    private String redact(String value) {
        if (value == null) return null;
        return value.replaceAll("(?i)(password|contraseña|secret|token)(\\s*[:=]\\s*)\\S+", "$1$2[omitido]")
                .replaceAll("(?<![\\w-])[0-9]{8}(?![\\w-])", "[documento omitido]");
    }

    private String limit(String value, int maximum) {
        if (value == null) return null;
        String safe = value.replaceAll("\\p{Cntrl}", " ").trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }
}
