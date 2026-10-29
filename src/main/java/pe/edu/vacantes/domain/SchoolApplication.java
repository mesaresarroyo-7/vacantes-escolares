package pe.edu.vacantes.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
import java.util.UUID;
@Entity @Table(name="school_applications",indexes={@Index(name="idx_application_guardian",columnList="guardian_id"),@Index(name="idx_application_status",columnList="status")})
@Getter @Setter @NoArgsConstructor
public class SchoolApplication {
    @Id @Column(length=36) private String id=UUID.randomUUID().toString();
    @Version private long version;
    @Column(nullable=false,unique=true,length=30) private String reference;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="guardian_id",nullable=false) private UserAccount guardian;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="offer_id",nullable=false) private AcademicOffer offer;
    @Column(name="student_first_names",nullable=false,length=80) private String studentFirstNames;
    @Column(name="student_last_names",nullable=false,length=100) private String studentLastNames;
    @Column(name="student_document",nullable=false,length=8) private String studentDocument;
    @Column(name="birth_date",nullable=false) private LocalDate birthDate;
    @Column(name="guardian_message",length=1200) private String guardianMessage;
    @Column(name="review_note",length=1200) private String reviewNote;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ApplicationStatus status=ApplicationStatus.ENVIADA;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
    @Column(name="updated_at",nullable=false) private Instant updatedAt=Instant.now();
    @PreUpdate public void touch() { updatedAt=Instant.now(); }
    public String getStudentFullName() { return studentFirstNames+" "+studentLastNames; }
    public boolean isEditable() { return status==ApplicationStatus.ENVIADA || status==ApplicationStatus.OBSERVADA; }
    public boolean isWithdrawable() { return isEditable() || status==ApplicationStatus.EN_REVISION; }
}
