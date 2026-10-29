package pe.edu.vacantes.web;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.vacantes.domain.ApplicationStatus;
import pe.edu.vacantes.domain.SchoolApplication;
import pe.edu.vacantes.form.ApplicationForm;
import pe.edu.vacantes.repository.AcademicOfferRepository;
import pe.edu.vacantes.service.ApplicationService;

@Controller
@RequestMapping("/solicitudes")
public class ApplicationController {

    private final ApplicationService service;
    private final AcademicOfferRepository offers;

    public ApplicationController(ApplicationService service, AcademicOfferRepository offers) {
        this.service = service;
        this.offers = offers;
    }

    @GetMapping
    public String list(Principal principal, Model model) {
        List<SchoolApplication> applications = service.listForGuardian(principal.getName());
        model.addAttribute("applications", applications);
        model.addAttribute("total", applications.size());
        model.addAttribute("pending", applications.stream().filter(SchoolApplication::isWithdrawable).count());
        model.addAttribute("approved", applications.stream()
                .filter(application -> application.getStatus() == ApplicationStatus.APROBADA).count());
        model.addAttribute("pageTitle", "Mis solicitudes");
        return "guardian/list";
    }

    @GetMapping("/nueva")
    public String newApplication(Model model) {
        model.addAttribute("applicationForm", new ApplicationForm());
        return form(model, false, null);
    }

    @PostMapping("/nueva")
    public String create(@Valid @ModelAttribute("applicationForm") ApplicationForm applicationForm,
                         BindingResult errors, Principal principal, Model model,
                         RedirectAttributes flash) {
        if (errors.hasErrors()) {
            return form(model, false, null);
        }
        try {
            SchoolApplication application = service.create(applicationForm, principal.getName());
            flash.addFlashAttribute("success",
                    "Tu solicitud fue enviada. Puedes consultar su estado desde esta página.");
            return "redirect:/solicitudes/" + application.getId();
        } catch (IllegalArgumentException exception) {
            errors.reject("application.invalid", exception.getMessage());
            return form(model, false, null);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Principal principal, Model model) {
        SchoolApplication application = service.getForGuardian(id, principal.getName());
        model.addAttribute("schoolApplication", application);
        model.addAttribute("history", service.history(id));
        model.addAttribute("staffView", false);
        model.addAttribute("pageTitle", application.getReference());
        return "application/detail";
    }

    @GetMapping("/{id}/editar")
    public String edit(@PathVariable String id, Principal principal, Model model) {
        SchoolApplication application = service.getForGuardian(id, principal.getName());
        if (!application.isEditable()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Esta solicitud ya no admite cambios. Revisa su estado actual.");
        }
        ApplicationForm applicationForm = new ApplicationForm();
        applicationForm.setOfferId(application.getOffer().getId());
        applicationForm.setStudentFirstNames(application.getStudentFirstNames());
        applicationForm.setStudentLastNames(application.getStudentLastNames());
        applicationForm.setStudentDocument(application.getStudentDocument());
        applicationForm.setBirthDate(application.getBirthDate());
        applicationForm.setGuardianMessage(application.getGuardianMessage());
        applicationForm.setVersion(application.getVersion());
        model.addAttribute("applicationForm", applicationForm);
        return form(model, true, id);
    }

    @PostMapping("/{id}/editar")
    public String update(@PathVariable String id,
                         @Valid @ModelAttribute("applicationForm") ApplicationForm applicationForm,
                         BindingResult errors, Principal principal, Model model,
                         RedirectAttributes flash) {
        // Check ownership before returning a form with validation errors, too.
        service.getForGuardian(id, principal.getName());
        if (errors.hasErrors()) {
            return form(model, true, id);
        }
        try {
            service.update(id, applicationForm, principal.getName());
            flash.addFlashAttribute("success", "Los cambios fueron enviados a admisiones.");
            return "redirect:/solicitudes/" + id;
        } catch (IllegalArgumentException exception) {
            errors.reject("application.invalid", exception.getMessage());
            return form(model, true, id);
        }
    }

    @PostMapping("/{id}/retirar")
    public String withdraw(@PathVariable String id, @RequestParam Long version,
                           Principal principal, RedirectAttributes flash) {
        service.withdraw(id, version, principal.getName());
        flash.addFlashAttribute("success", "La solicitud fue retirada.");
        return "redirect:/solicitudes/" + id;
    }

    private String form(Model model, boolean editing, String id) {
        model.addAttribute("offers", offers.findByActiveTrueOrderByIdAsc());
        model.addAttribute("editing", editing);
        model.addAttribute("applicationId", id);
        model.addAttribute("pageTitle", editing ? "Actualizar solicitud" : "Nueva solicitud");
        model.addAttribute("formAction", editing
                ? "/solicitudes/" + id + "/editar" : "/solicitudes/nueva");
        return "guardian/form";
    }
}
