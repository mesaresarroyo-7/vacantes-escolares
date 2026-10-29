package pe.edu.vacantes.exception;

import org.springframework.http.HttpStatus;

public final class InvalidApplicationStateException extends BusinessException {
    public enum Reason {
        NOT_EDITABLE("Esta solicitud ya no admite cambios. Revisa su estado actual."),
        NOT_WITHDRAWABLE("Esta solicitud ya tiene un resultado definitivo y no puede retirarse."),
        NOT_REVIEWABLE("La solicitud tiene un resultado definitivo y ya no admite revisión."),
        STALE_VERSION("La solicitud cambió mientras la revisabas. Actualiza la página antes de continuar.");

        private final String message;

        Reason(String message) {
            this.message = message;
        }
    }

    public InvalidApplicationStateException(Reason reason) {
        super("ESTADO_SOLICITUD_INVALIDO", HttpStatus.CONFLICT, reason.message);
    }
}
