package pe.edu.vacantes.form;
import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
@Getter @Setter
public class ApplicationForm {
    @NotNull(message="Selecciona un grado.") @Positive private Long offerId;
    @NotBlank(message="Escribe los nombres del estudiante.") @Size(min=2,max=80)
    @Pattern(regexp="[\\p{L}\\p{M} .'-]+",message="Usa letras y espacios para los nombres.") private String studentFirstNames;
    @NotBlank(message="Escribe los apellidos del estudiante.") @Size(min=2,max=100)
    @Pattern(regexp="[\\p{L}\\p{M} .'-]+",message="Usa letras y espacios para los apellidos.") private String studentLastNames;
    @NotBlank(message="Escribe el DNI del estudiante.") @Pattern(regexp="[0-9]{8}",message="El DNI debe tener exactamente 8 dígitos.") private String studentDocument;
    @NotNull(message="Indica la fecha de nacimiento.") @Past(message="La fecha debe ser anterior a hoy.")
    @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) private LocalDate birthDate;
    @Size(max=1200,message="El comentario puede tener hasta 1200 caracteres.") private String guardianMessage;
    private Long version;
}
