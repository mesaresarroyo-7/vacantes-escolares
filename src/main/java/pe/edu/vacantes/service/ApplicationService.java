package pe.edu.vacantes.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.vacantes.domain.AcademicOffer;
import pe.edu.vacantes.domain.ApplicationEvent;
import pe.edu.vacantes.domain.ApplicationStatus;
import pe.edu.vacantes.domain.Role;
import pe.edu.vacantes.domain.SchoolApplication;
import pe.edu.vacantes.domain.UserAccount;
import pe.edu.vacantes.exception.ApplicationNotFoundException;
import pe.edu.vacantes.exception.DuplicateApplicationException;
import pe.edu.vacantes.exception.InvalidApplicationStateException;
import pe.edu.vacantes.exception.InvalidApplicationStateException.Reason;
import pe.edu.vacantes.exception.NoVacanciesException;
import pe.edu.vacantes.form.ApplicationForm;
import pe.edu.vacantes.form.ReviewForm;
import pe.edu.vacantes.repository.AcademicOfferRepository;
import pe.edu.vacantes.repository.ApplicationEventRepository;
import pe.edu.vacantes.repository.SchoolApplicationRepository;
import pe.edu.vacantes.repository.UserAccountRepository;

/**
 * Business rules live here as well as in the forms: ownership and workflow must
 * hold even when a caller bypasses a page or tampers with its hidden inputs.
 */
@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private static final int SCHOOL_YEAR = 2027;
    private static final Set<ApplicationStatus> ACTIVE = EnumSet.of(
            ApplicationStatus.ENVIADA, ApplicationStatus.EN_REVISION,
            ApplicationStatus.OBSERVADA, ApplicationStatus.APROBADA);
    private static final Set<ApplicationStatus> REVIEW_TARGETS = EnumSet.of(
            ApplicationStatus.EN_REVISION, ApplicationStatus.OBSERVADA,
            ApplicationStatus.APROBADA, ApplicationStatus.RECHAZADA);

    private final SchoolApplicationRepository applications;
    private final UserAccountRepository users;
    private final AcademicOfferRepository offers;
    private final ApplicationEventRepository events;
    private final AuditService audit;

    @PersistenceContext
    private EntityManager entityManager;

    public ApplicationService(SchoolApplicationRepository applications,
                              UserAccountRepository users,
                              AcademicOfferRepository offers,
                              ApplicationEventRepository events,
                              AuditService audit) {
        this.applications = applications;
        this.users = users;
        this.offers = offers;
        this.events = events;
        this.audit = audit;
    }

    public List<SchoolApplication> listForGuardian(String email) {
        requireGuardian(email);
        return applications.findByGuardianEmailIgnoreCaseOrderByCreatedAtDesc(email)
                .stream().map(this::forView).toList();
    }

    public SchoolApplication getForGuardian(String id, String email) {
        requireGuardian(email);
        return forView(applications.findByIdAndGuardianEmailIgnoreCase(id, email)
                .orElseThrow(this::notFound));
    }

    public List<SchoolApplication> listForStaff(String query, String status) {
        String search = clean(query);
        if (search != null && search.length() > 80) {
            throw new IllegalArgumentException("La búsqueda admite hasta 80 caracteres.");
        }
        ApplicationStatus selected = null;
        String statusValue = clean(status);
        if (statusValue != null) {
            try {
                selected = ApplicationStatus.valueOf(statusValue);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Selecciona un estado válido.");
            }
        }
        final ApplicationStatus filter = selected;
        final String needle = search == null ? null : search.toLowerCase(Locale.ROOT);
        return applications.findAllByOrderByCreatedAtDesc().stream()
                .filter(application -> filter == null || application.getStatus() == filter)
                .filter(application -> needle == null || matches(application, needle))
                .map(this::forView)
                .toList();
    }

    public SchoolApplication getForStaff(String id) {
        return forView(applications.findById(id).orElseThrow(this::notFound));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SchoolApplication create(ApplicationForm form, String email) {
        audit.record(email, "SOLICITUD_INTENTO_CREAR", null, "Inicio del envío de solicitud.");
        UserAccount guardian = requireGuardian(email);
        // Serialize this guardian's submissions so two simultaneous requests
        // cannot create duplicate active applications for the same student.
        entityManager.lock(guardian, LockModeType.PESSIMISTIC_WRITE);
        validateStudent(form);
        AcademicOffer offer = requireOffer(form.getOfferId());
        ensureNoDuplicate(guardian, form.getStudentDocument().trim(), offer);

        Instant now = Instant.now();
        SchoolApplication application = new SchoolApplication();
        application.setId(UUID.randomUUID().toString());
        application.setReference("VAC-" + SCHOOL_YEAR + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        application.setGuardian(guardian);
        application.setOffer(offer);
        copyStudent(form, application);
        application.setStatus(ApplicationStatus.ENVIADA);
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        application = applications.saveAndFlush(application);
        addEvent(application, guardian, null, ApplicationStatus.ENVIADA,
                "El apoderado envió la solicitud de vacante.");
        events.flush();
        audit.recordAfterCommit(guardian.getEmail(), "SOLICITUD_CREADA", application.getId(),
                application.getReference() + ": solicitud enviada.");
        return forView(application);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SchoolApplication update(String id, ApplicationForm form, String email) {
        audit.record(email, "SOLICITUD_INTENTO_EDITAR", id, "Inicio de actualización de solicitud.");
        SchoolApplication application = getForGuardian(id, email);
        UserAccount guardian = application.getGuardian();
        entityManager.lock(guardian, LockModeType.PESSIMISTIC_WRITE);
        requireVersion(application, form.getVersion());
        if (!application.isEditable()) {
            throw new InvalidApplicationStateException(Reason.NOT_EDITABLE);
        }
        validateStudent(form);
        AcademicOffer offer = requireOffer(form.getOfferId());
        String document = form.getStudentDocument().trim();
        if (!document.equals(application.getStudentDocument())
                || offer.getSchoolYear() != application.getOffer().getSchoolYear()) {
            ensureNoDuplicate(guardian, document, offer);
        }

        ApplicationStatus previous = application.getStatus();
        application.setOffer(offer);
        copyStudent(form, application);
        application.setStatus(ApplicationStatus.ENVIADA);
        application.setReviewNote(null);
        application.setUpdatedAt(Instant.now());
        application = applications.saveAndFlush(application);
        addEvent(application, guardian, previous, ApplicationStatus.ENVIADA,
                previous == ApplicationStatus.OBSERVADA
                        ? "El apoderado subsanó las observaciones y reenvió la solicitud."
                        : "El apoderado actualizó los datos de la solicitud.");
        events.flush();
        audit.recordAfterCommit(guardian.getEmail(), "SOLICITUD_ACTUALIZADA", application.getId(),
                application.getReference() + ": datos actualizados y solicitud enviada.");
        return forView(application);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void withdraw(String id, Long version, String email) {
        audit.record(email, "SOLICITUD_INTENTO_RETIRAR", id, "Inicio del retiro de solicitud.");
        SchoolApplication application = getForGuardian(id, email);
        entityManager.lock(application.getGuardian(), LockModeType.PESSIMISTIC_WRITE);
        requireVersion(application, version);
        if (!application.isWithdrawable()) {
            throw new InvalidApplicationStateException(Reason.NOT_WITHDRAWABLE);
        }
        ApplicationStatus previous = application.getStatus();
        application.setStatus(ApplicationStatus.RETIRADA);
        application.setUpdatedAt(Instant.now());
        applications.saveAndFlush(application);
        addEvent(application, application.getGuardian(), previous, ApplicationStatus.RETIRADA,
                "El apoderado retiró la solicitud.");
        events.flush();
        audit.recordAfterCommit(email, "SOLICITUD_RETIRADA", application.getId(),
                application.getReference() + ": retirada por el apoderado.");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void review(String id, ReviewForm form, String staffEmail) {
        audit.record(staffEmail, "SOLICITUD_INTENTO_REVISAR", id, "Inicio de revisión de solicitud.");
        UserAccount reviewer = requireStaff(staffEmail);
        SchoolApplication application = getForStaff(id);
        requireVersion(application, form.getVersion());
        if (!application.isWithdrawable()) {
            throw new InvalidApplicationStateException(Reason.NOT_REVIEWABLE);
        }
        ApplicationStatus next = form.getStatus();
        if (next == null || !REVIEW_TARGETS.contains(next)) {
            throw new IllegalArgumentException("Selecciona un estado de revisión válido.");
        }
        String note = clean(form.getNote());
        if (note != null && note.length() > 1200) {
            throw new IllegalArgumentException("La observación admite hasta 1200 caracteres.");
        }
        if ((next == ApplicationStatus.OBSERVADA || next == ApplicationStatus.RECHAZADA)
                && note == null) {
            throw new IllegalArgumentException(
                    "Escribe una observación para explicar qué debe corregirse o el motivo del rechazo.");
        }
        if (next == ApplicationStatus.APROBADA) {
            // Lock the offer, rather than merely counting seats: this serializes
            // approvals for different applications competing for the last seat.
            AcademicOffer offer = offers.findLockedById(application.getOffer().getId())
                    .orElseThrow(this::notFound);
            if (!offer.isActive() || offer.getSchoolYear() != SCHOOL_YEAR) {
                throw new IllegalArgumentException("La oferta seleccionada ya no está disponible.");
            }
            if (applications.countByOfferIdAndStatus(offer.getId(), ApplicationStatus.APROBADA)
                    >= offer.getCapacity()) {
                throw new NoVacanciesException();
            }
        }

        ApplicationStatus previous = application.getStatus();
        if (previous == next && java.util.Objects.equals(note, application.getReviewNote())) {
            throw new IllegalArgumentException("No hay cambios de estado u observación para guardar.");
        }
        application.setStatus(next);
        application.setReviewNote(note);
        application.setUpdatedAt(Instant.now());
        applications.saveAndFlush(application);
        addEvent(application, reviewer, previous, next,
                note == null ? "Admisiones actualizó el estado a " + next.getLabel() + "." : note);
        events.flush();
        audit.recordAfterCommit(reviewer.getEmail(), "SOLICITUD_REVISADA", application.getId(),
                application.getReference() + ": " + previous.name() + " → " + next.name() + ".");
    }

    /** Callers must authorize access to the application before requesting history. */
    public List<ApplicationEvent> history(String id) {
        return events.findByApplicationIdOrderByCreatedAtAsc(id).stream().peek(event -> {
            if (event.getActor() != null) {
                event.getActor().getFullName();
            }
        }).toList();
    }

    private UserAccount requireGuardian(String email) {
        UserAccount user = users.findByEmailIgnoreCase(email).orElseThrow(this::notFound);
        if (!user.isEnabled() || user.getRole() != Role.APODERADO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Esta operación está disponible para apoderados con una cuenta activa.");
        }
        return user;
    }

    private UserAccount requireStaff(String email) {
        UserAccount user = users.findByEmailIgnoreCase(email).orElseThrow(this::notFound);
        if (!user.isEnabled() || (user.getRole() != Role.ADMISION && user.getRole() != Role.ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Necesitas una cuenta activa de admisiones.");
        }
        return user;
    }

    private AcademicOffer requireOffer(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("Selecciona el grado al que deseas postular.");
        }
        AcademicOffer offer = offers.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Selecciona una oferta disponible."));
        if (!offer.isActive() || offer.getSchoolYear() != SCHOOL_YEAR) {
            throw new IllegalArgumentException("La oferta seleccionada ya no está disponible.");
        }
        return offer;
    }

    private void ensureNoDuplicate(UserAccount guardian, String document, AcademicOffer offer) {
        if (applications.existsByGuardianIdAndStudentDocumentAndOfferSchoolYearAndStatusIn(
                guardian.getId(), document, offer.getSchoolYear(), ACTIVE)) {
            throw new DuplicateApplicationException();
        }
    }

    private void validateStudent(ApplicationForm form) {
        requireText(form.getStudentFirstNames(), "los nombres del estudiante", 80);
        requireText(form.getStudentLastNames(), "los apellidos del estudiante", 100);
        if (form.getStudentDocument() == null
                || !form.getStudentDocument().trim().matches("[0-9]{8}")) {
            throw new IllegalArgumentException("El DNI del estudiante debe tener 8 dígitos.");
        }
        LocalDate birthDate = form.getBirthDate();
        if (birthDate == null
                || !birthDate.isBefore(LocalDate.now(ZoneId.of("America/Lima")))) {
            throw new IllegalArgumentException("Ingresa una fecha de nacimiento anterior a hoy.");
        }
        int age = Period.between(birthDate, LocalDate.of(SCHOOL_YEAR, 3, 1)).getYears();
        if (age < 3 || age > 20) {
            throw new IllegalArgumentException(
                    "Para esta demostración, el estudiante debe tener entre 3 y 20 años al 1 de marzo de 2027.");
        }
        if (form.getGuardianMessage() != null && form.getGuardianMessage().length() > 1200) {
            throw new IllegalArgumentException("El mensaje admite hasta 1200 caracteres.");
        }
    }

    private void requireText(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max) {
            throw new IllegalArgumentException("Revisa " + label + " (máximo " + max + " caracteres).");
        }
    }

    private void requireVersion(SchoolApplication application, Long version) {
        if (version == null || version.longValue() != application.getVersion()) {
            throw new InvalidApplicationStateException(Reason.STALE_VERSION);
        }
    }

    private void copyStudent(ApplicationForm form, SchoolApplication application) {
        application.setStudentFirstNames(form.getStudentFirstNames().trim());
        application.setStudentLastNames(form.getStudentLastNames().trim());
        application.setStudentDocument(form.getStudentDocument().trim());
        application.setBirthDate(form.getBirthDate());
        application.setGuardianMessage(clean(form.getGuardianMessage()));
    }

    private void addEvent(SchoolApplication application, UserAccount actor,
                          ApplicationStatus previous, ApplicationStatus next, String note) {
        ApplicationEvent event = new ApplicationEvent();
        event.setApplication(application);
        event.setActor(actor);
        event.setFromStatus(previous);
        event.setToStatus(next);
        event.setNote(note);
        event.setCreatedAt(Instant.now());
        events.save(event);
    }

    private boolean matches(SchoolApplication application, String needle) {
        return (application.getReference() + " " + application.getStudentFullName()
                + " " + application.getGuardian().getFullName()
                + " " + application.getGuardian().getEmail())
                .toLowerCase(Locale.ROOT).contains(needle);
    }

    private SchoolApplication forView(SchoolApplication application) {
        application.getOffer().getGrade();
        application.getGuardian().getFullName();
        return application;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ApplicationNotFoundException notFound() {
        // Use the same response for an unknown ID and another guardian's ID.
        return new ApplicationNotFoundException();
    }
}
