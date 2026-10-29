package pe.edu.vacantes.web;

import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView status(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        return view(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                exception.getReason());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden(AccessDeniedException exception) {
        return view(HttpStatus.FORBIDDEN, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView missing(NoResourceFoundException exception) {
        return view(HttpStatus.NOT_FOUND, null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ModelAndView stale(Exception exception) {
        return view(HttpStatus.CONFLICT,
                "La solicitud cambió mientras la revisabas. Actualiza la página antes de continuar.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integrity(DataIntegrityViolationException exception) {
        return view(HttpStatus.CONFLICT,
                "No se guardaron los cambios porque los datos entran en conflicto con un registro existente.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ModelAndView invalid(Exception exception) {
        return view(HttpStatus.BAD_REQUEST, "Revisa los datos enviados e inténtalo nuevamente.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception exception) {
        LOGGER.error("Error inesperado de tipo {}.", exception.getClass().getSimpleName());
        return view(HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    static ModelAndView view(HttpStatus status, String message) {
        ModelAndView page = new ModelAndView("error/error");
        page.setStatus(status);
        page.addObject("status", status.value());
        page.addObject("title", switch (status.value()) {
            case 400 -> "Revisa la solicitud";
            case 403 -> "No tienes acceso";
            case 404 -> "No encontramos esta página";
            case 409 -> "La información cambió";
            case 429 -> "Espera antes de volver a intentar";
            default -> "No pudimos completar la operación";
        });
        page.addObject("message", message != null && !message.isBlank() ? message : switch (status.value()) {
            case 400 -> "Los datos enviados no son válidos.";
            case 403 -> "Tu cuenta no tiene permiso para realizar esta acción.";
            case 404 -> "La página o solicitud que buscas no está disponible.";
            case 409 -> "Actualiza la página para consultar la información más reciente.";
            case 429 -> "Se alcanzó el límite de intentos de acceso. Inténtalo nuevamente en cinco minutos.";
            default -> "Ocurrió un problema. Inténtalo nuevamente en unos momentos.";
        });
        page.addObject("pageTitle", "Solicitud no completada");
        return page;
    }
}
