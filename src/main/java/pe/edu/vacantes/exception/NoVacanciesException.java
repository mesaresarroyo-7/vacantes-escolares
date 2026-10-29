package pe.edu.vacantes.exception;

import org.springframework.http.HttpStatus;

public final class NoVacanciesException extends BusinessException {
    public NoVacanciesException() {
        super("SIN_VACANTES", HttpStatus.BAD_REQUEST,
                "No quedan vacantes disponibles en este grado. No se guardó la aprobación.");
    }
}
