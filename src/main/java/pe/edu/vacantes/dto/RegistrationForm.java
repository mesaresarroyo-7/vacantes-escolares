package pe.edu.vacantes.dto;
import jakarta.validation.constraints.*;
import lombok.*;
@Getter @Setter
public class RegistrationForm {
    @NotBlank(message="Escribe tus nombres y apellidos.") @Size(min=3,max=100,message="Usa entre 3 y 100 caracteres.")
    @Pattern(regexp="[\\p{L}\\p{M} .'-]+",message="Usa letras y espacios para tu nombre.") private String fullName;
    @NotBlank(message="Escribe tu correo.") @Email(message="Escribe un correo válido.") @Size(max=190) private String email;
    @NotBlank(message="Escribe tu celular.") @Pattern(regexp="9[0-9]{8}",message="Escribe un celular peruano de 9 dígitos que empiece con 9.") private String phone;
    @NotBlank(message="Escribe una contraseña.") @Size(min=10,max=128,message="La contraseña debe tener entre 10 y 128 caracteres.") private String password;
    @NotBlank(message="Repite tu contraseña.") @Size(max=128) private String confirmPassword;
    @Size(max=20) private String captchaAnswer;
}
