package pe.edu.vacantes.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="audit_events") @Getter @Setter @NoArgsConstructor
public class AuditEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=190) private String actor;
    @Column(nullable=false,length=50) private String action;
    @Column(length=100) private String target;
    @Column(length=250) private String detail;
    @Column(nullable=false,length=10) private String level="INFO";
    @Column(name="origin_ip",length=64) private String originIp;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
