package pe.edu.vacantes.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class PortalErrorController implements ErrorController {
    @RequestMapping("/error")
    public ModelAndView error(HttpServletRequest request) {
        Object raw = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int value = raw instanceof Number number ? number.intValue() : 500;
        HttpStatus status = HttpStatus.resolve(value);
        return GlobalExceptionHandler.view(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, null);
    }
}
