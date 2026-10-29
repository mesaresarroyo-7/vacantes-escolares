package pe.edu.vacantes.repository;
import pe.edu.vacantes.domain.*;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface SchoolApplicationRepository extends JpaRepository<SchoolApplication,String> {
    @EntityGraph(attributePaths={"guardian","offer"})
    List<SchoolApplication> findByGuardianEmailIgnoreCaseOrderByCreatedAtDesc(String email);
    @EntityGraph(attributePaths={"guardian","offer"})
    Optional<SchoolApplication> findByIdAndGuardianEmailIgnoreCase(String id,String email);
    @EntityGraph(attributePaths={"guardian","offer"})
    List<SchoolApplication> findAllByOrderByCreatedAtDesc();
    @Override @EntityGraph(attributePaths={"guardian","offer"}) Optional<SchoolApplication> findById(String id);
    long countByStatus(ApplicationStatus status);
    long countByOfferIdAndStatus(Long offerId,ApplicationStatus status);
    boolean existsByGuardianIdAndStudentDocumentAndOfferSchoolYearAndStatusIn(Long guardianId,String document,int year,Collection<ApplicationStatus> statuses);
}
