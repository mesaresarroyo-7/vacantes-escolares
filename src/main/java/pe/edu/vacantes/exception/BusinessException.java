package pe.edu.vacantes.exception;

import org.springframework.http.HttpStatus;

/** A business failure with a stable audit code and a message suitable for the page. */
public abstract class BusinessException extends IllegalArgumentException {
    private final String code;
    private final HttpStatus status;

    protected BusinessException(String code, HttpStatus status, String safeMessage) {
        super(safeMessage);
        this.code = code;
        this.status = status;
    }

    public final String getCode() {
        return code;
    }

    public final HttpStatus getStatus() {
        return status;
    }
}
