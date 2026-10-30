package pe.edu.vacantes.controller;

import jakarta.servlet.http.HttpServletRequest;
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
import pe.edu.vacantes.exception.BusinessException;
import pe.edu.vacantes.service.AuditService;

@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final AuditService audit;

    public GlobalExceptionHandler(AuditService audit) {
        this.audit = audit;
    }

    @ExceptionHandler(BusinessException.class)
    public ModelAndView business(BusinessException exception, HttpServletRequest request) {
        record(request, "NEGOCIO_RECHAZADO", exception.getCode(), exception.getMessage(), "WARN");
        return view(exception.getStatus(), exception.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView status(ResponseStatusException exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        record(request, "PETICION_RECHAZADA", "HTTP_" + exception.getStatusCode().value(),
                "La petición no pudo completarse.", "WARN");
        return view(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status,
                exception.getReason());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ModelAndView forbidden(AccessDeniedException exception, HttpServletRequest request) {
        record(request, "ACCESO_DENEGADO", "HTTP_403", "Operación sin permiso.", "WARN");
        return view(HttpStatus.FORBIDDEN, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView missing(NoResourceFoundException exception) {
        return view(HttpStatus.NOT_FOUND, null);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
    public ModelAndView stale(Exception exception, HttpServletRequest request) {
        record(request, "NEGOCIO_RECHAZADO", "VERSION_DESACTUALIZADA",
                "La solicitud cambió durante la operación.", "WARN");
        return view(HttpStatus.CONFLICT,
                "La solicitud cambió mientras la revisabas. Actualiza la página antes de continuar.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ModelAndView integrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        record(request, "NEGOCIO_RECHAZADO", "CONFLICTO_DATOS",
                "Los datos entran en conflicto con un registro existente.", "WARN");
        return view(HttpStatus.CONFLICT,
                "No se guardaron los cambios porque los datos entran en conflicto con un registro existente.");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ModelAndView invalid(Exception exception, HttpServletRequest request) {
        record(request, "DATOS_INVALIDOS", "HTTP_400", "Datos de entrada no válidos.", "WARN");
        return view(HttpStatus.BAD_REQUEST, "Revisa los datos enviados e inténtalo nuevamente.");
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView unexpected(Exception exception, HttpServletRequest request) {
        LOGGER.error("Error inesperado de tipo {}.", exception.getClass().getSimpleName());
        record(request, "ERROR_INTERNO", "HTTP_500", "Operación no completada por un error interno.", "ERROR");
        return view(HttpStatus.INTERNAL_SERVER_ERROR, null);
    }

    private void record(HttpServletRequest request, String action, String code, String detail, String level) {
        String actor = request.getUserPrincipal() == null ? "anonimo" : request.getUserPrincipal().getName();
        try {
            // The service transaction has already rolled back before MVC handles the exception.
            audit.record(actor, action, code, detail, level, request.getRemoteAddr());
        } catch (RuntimeException auditFailure) {
            // A database outage must not replace the friendly error page or reveal its diagnostics.
            LOGGER.error("No se pudo guardar la auditoría de {}. Tipo={}", code,
                    auditFailure.getClass().getSimpleName());
        }
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
