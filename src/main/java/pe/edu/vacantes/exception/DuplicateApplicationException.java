package pe.edu.vacantes.exception;

import org.springframework.http.HttpStatus;

public final class DuplicateApplicationException extends BusinessException {
    public DuplicateApplicationException() {
        super("SOLICITUD_DUPLICADA", HttpStatus.BAD_REQUEST,
                "Ya tienes una solicitud activa para este estudiante en el mismo año escolar.");
    }
}
