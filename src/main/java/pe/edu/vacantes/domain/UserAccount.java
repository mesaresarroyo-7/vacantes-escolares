package pe.edu.vacantes.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="app_users") @Getter @Setter @NoArgsConstructor
public class UserAccount {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,unique=true,length=190) private String email;
    @Column(name="password_hash",nullable=false,length=255) private String passwordHash;
    @Column(name="full_name",nullable=false,length=100) private String fullName;
    @Column(nullable=false,length=20) private String phone;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private Role role=Role.APODERADO;
    @Column(nullable=false) private boolean enabled=true;
    @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt=Instant.now();
}
