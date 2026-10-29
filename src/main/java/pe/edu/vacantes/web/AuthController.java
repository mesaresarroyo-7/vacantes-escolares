package pe.edu.vacantes.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.vacantes.domain.Role;
import pe.edu.vacantes.domain.UserAccount;
import pe.edu.vacantes.form.RegistrationForm;
import pe.edu.vacantes.repository.UserAccountRepository;
import pe.edu.vacantes.service.AccountService;
import pe.edu.vacantes.service.CaptchaService;

@Controller
public class AuthController {
    private final AccountService accounts;
    private final CaptchaService captcha;
    private final UserAccountRepository users;

    public AuthController(AccountService accounts, CaptchaService captcha, UserAccountRepository users) {
        this.accounts = accounts;
        this.captcha = captcha;
        this.users = users;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("pageTitle", "Admisión escolar");
        return "index";
    }

    @GetMapping("/login")
    public String login(Authentication authentication, HttpServletRequest request, Model model) {
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) return "redirect:/panel";
        if (request.getParameter("disabled") != null) {
            model.addAttribute("error", "Tu cuenta no está activa. Comunícate con admisiones.");
        }
        model.addAttribute("pageTitle", "Ingresar");
        return "auth/login";
    }

    @GetMapping("/registro")
    public String register(HttpSession session, Model model) {
        model.addAttribute("registrationForm", new RegistrationForm());
        model.addAttribute("pageTitle", "Crear cuenta");
        captcha.prepare(session, model);
        return "auth/register";
    }

    @PostMapping("/registro")
    public String register(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                           BindingResult errors,
                           @RequestParam(name = "g-recaptcha-response", required = false) String googleToken,
                           HttpSession session, Model model, RedirectAttributes flash) {
        if (!captcha.verify(session, form.getCaptchaAnswer(), googleToken)) {
            errors.rejectValue("captchaAnswer", "captcha.invalid",
                    "Completa nuevamente la verificación de registro.");
        }
        if (!errors.hasErrors()) {
            try {
                accounts.register(form);
                form.setPassword(null);
                form.setConfirmPassword(null);
                flash.addFlashAttribute("success", "Tu cuenta fue creada. Ya puedes ingresar y solicitar una vacante.");
                return "redirect:/login";
            } catch (IllegalArgumentException exception) {
                errors.reject("registration.invalid", exception.getMessage());
            }
        }
        form.setPassword(null);
        form.setConfirmPassword(null);
        form.setCaptchaAnswer(null);
        model.addAttribute("pageTitle", "Crear cuenta");
        captcha.prepare(session, model);
        return "auth/register";
    }

    @GetMapping("/panel")
    public String panel(Principal principal) {
        UserAccount account = users.findByEmailIgnoreCase(principal.getName()).orElseThrow();
        return account.getRole() == Role.APODERADO ? "redirect:/solicitudes" : "redirect:/admision";
    }
}
