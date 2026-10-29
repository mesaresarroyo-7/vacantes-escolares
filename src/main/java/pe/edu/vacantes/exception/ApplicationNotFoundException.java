package pe.edu.vacantes.exception;

import org.springframework.http.HttpStatus;

/** Keeps absent and inaccessible applications indistinguishable to the caller. */
public final class ApplicationNotFoundException extends BusinessException {
    public ApplicationNotFoundException() {
        super("SOLICITUD_NO_ENCONTRADA", HttpStatus.NOT_FOUND,
                "No se encontró la solicitud.");
    }
}
