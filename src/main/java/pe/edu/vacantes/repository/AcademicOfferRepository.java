package pe.edu.vacantes.repository;
import pe.edu.vacantes.model.AcademicOffer;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface AcademicOfferRepository extends JpaRepository<AcademicOffer,Long> {
    List<AcademicOffer> findByActiveTrueOrderByIdAsc();
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from AcademicOffer o where o.id=:id")
    Optional<AcademicOffer> findLockedById(@Param("id") Long id);
}
