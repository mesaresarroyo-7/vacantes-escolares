package pe.edu.vacantes.domain;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="academic_offers",uniqueConstraints=@UniqueConstraint(columnNames={"grade","school_year","campus"}))
@Getter @Setter @NoArgsConstructor
public class AcademicOffer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR) @Enumerated(EnumType.STRING) @Column(nullable=false,length=25) private Grade grade;
    @Column(name="school_year",nullable=false) private int schoolYear=2027;
    @Column(nullable=false,length=100) private String campus="Sede de referencia";
    @Column(nullable=false) private int capacity=25;
    @Column(nullable=false) private boolean active=true;
}
