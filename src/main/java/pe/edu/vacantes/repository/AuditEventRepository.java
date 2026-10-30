package pe.edu.vacantes.repository;
import pe.edu.vacantes.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AuditEventRepository extends JpaRepository<AuditEvent,Long> {
    List<AuditEvent> findTop150ByOrderByCreatedAtDesc();
}
