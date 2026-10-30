package pe.edu.vacantes.controller;

import java.security.Principal;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.vacantes.model.Role;
import pe.edu.vacantes.model.UserAccount;
import pe.edu.vacantes.repository.AuditEventRepository;
import pe.edu.vacantes.repository.UserAccountRepository;
import pe.edu.vacantes.service.AuditService;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserAccountRepository users;
    private final AuditEventRepository audits;
    private final AuditService audit;

    public AdminController(UserAccountRepository users, AuditEventRepository audits, AuditService audit) {
        this.users = users;
        this.audits = audits;
        this.audit = audit;
    }

    @GetMapping("/usuarios")
    public String users(Principal principal, Model model) {
        requireAdmin(principal);
        model.addAttribute("users", users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
        model.addAttribute("pageTitle", "Usuarios");
        return "admin/users";
    }

    @PostMapping("/usuarios/{id}/estado")
    @Transactional
    public String toggle(@PathVariable Long id, @RequestParam boolean enabled,
                         Principal principal, RedirectAttributes flash) {
        UserAccount actor = requireAdmin(principal);
        UserAccount target = users.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontró la cuenta."));
        if (target.getId().equals(actor.getId()) || target.getRole() != Role.APODERADO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Desde esta pantalla solo puedes cambiar el estado de cuentas de apoderados.");
        }
        target.setEnabled(enabled);
        users.saveAndFlush(target);
        audit.recordAfterCommit(actor.getEmail(), enabled ? "CUENTA_ACTIVADA" : "CUENTA_DESACTIVADA",
                target.getId().toString(), "Se actualizó el estado de una cuenta de apoderado.");
        flash.addFlashAttribute("success", enabled ? "La cuenta fue activada." : "La cuenta fue desactivada.");
        return "redirect:/admin/usuarios";
    }

    @GetMapping("/auditoria")
    public String audit(Principal principal, Model model) {
        requireAdmin(principal);
        model.addAttribute("events", audits.findTop150ByOrderByCreatedAtDesc());
        model.addAttribute("pageTitle", "Registro de actividad");
        return "admin/audit";
    }

    private UserAccount requireAdmin(Principal principal) {
        UserAccount actor = users.findByEmailIgnoreCase(principal.getName()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta sección."));
        if (!actor.isEnabled() || actor.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a esta sección.");
        }
        return actor;
    }
}
