package pe.edu.vacantes.form;
import pe.edu.vacantes.domain.ApplicationStatus;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class ReviewForm {
    @NotNull(message="Selecciona el resultado de la revisión.") private ApplicationStatus status;
    @Size(max=1200,message="La observación puede tener hasta 1200 caracteres.") private String note;
    @NotNull(message="Actualiza la página antes de guardar la revisión.") private Long version;
}
