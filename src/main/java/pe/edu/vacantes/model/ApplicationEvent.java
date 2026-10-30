package pe.edu.vacantes.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="application_events") @Getter @Setter @NoArgsConstructor
public class ApplicationEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="application_id",nullable=false) private SchoolApplication application;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="actor_id",nullable=false) private UserAccount actor;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Enumerated(EnumType.STRING) @Column(name="from_status",length=20) private ApplicationStatus fromStatus;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Enumerated(EnumType.STRING) @Column(name="to_status",nullable=false,length=20) private ApplicationStatus toStatus;
    @Column(length=1200) private String note;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
