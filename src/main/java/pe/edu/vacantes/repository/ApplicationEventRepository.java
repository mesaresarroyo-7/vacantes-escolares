package pe.edu.vacantes.repository;
import pe.edu.vacantes.domain.ApplicationEvent;
import org.springframework.data.jpa.repository.*;
import java.util.List;
public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent,Long> {
    @EntityGraph(attributePaths={"actor"}) List<ApplicationEvent> findByApplicationIdOrderByCreatedAtAsc(String id);
}
