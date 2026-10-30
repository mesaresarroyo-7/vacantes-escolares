package pe.edu.vacantes.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.vacantes.model.Role;
import pe.edu.vacantes.model.UserAccount;
import pe.edu.vacantes.repository.UserAccountRepository;

@ControllerAdvice
public class GlobalModel {
    private final UserAccountRepository users;

    public GlobalModel(UserAccountRepository users) {
        this.users = users;
    }

    @ModelAttribute
    public void common(Model model, Authentication authentication, HttpServletRequest request) {
        UserAccount current = null;
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            current = users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        }
        model.addAttribute("currentUser", current);
        model.addAttribute("schoolYear", 2027);
        model.addAttribute("campusName", "Sede de referencia");
        model.addAttribute("brandName", "Vacantes escolares");
        model.addAttribute("referenceName", "Trilce (referencia académica)");
        model.addAttribute("staff", current != null
                && (current.getRole() == Role.ADMISION || current.getRole() == Role.ADMIN));
        model.addAttribute("admin", current != null && current.getRole() == Role.ADMIN);
        model.addAttribute("activePath", request.getRequestURI());
    }
}
