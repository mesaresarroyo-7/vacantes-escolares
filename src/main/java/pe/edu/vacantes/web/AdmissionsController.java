package pe.edu.vacantes.web;

import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import pe.edu.vacantes.domain.ApplicationStatus;
import pe.edu.vacantes.domain.SchoolApplication;
import pe.edu.vacantes.exception.BusinessException;
import pe.edu.vacantes.form.ReviewForm;
import pe.edu.vacantes.repository.SchoolApplicationRepository;
import pe.edu.vacantes.service.ApplicationService;

@Controller
@RequestMapping("/admision")
public class AdmissionsController {

    private static final List<ApplicationStatus> REVIEW_STATUSES = List.of(
            ApplicationStatus.EN_REVISION, ApplicationStatus.OBSERVADA,
            ApplicationStatus.APROBADA, ApplicationStatus.RECHAZADA);
    private final ApplicationService service;
    private final SchoolApplicationRepository applications;

    public AdmissionsController(ApplicationService service, SchoolApplicationRepository applications) {
        this.service = service;
        this.applications = applications;
    }

    @GetMapping
    public String list(@RequestParam(required = false, defaultValue = "") String query,
                       @RequestParam(required = false, defaultValue = "") String status,
                       Model model) {
        model.addAttribute("applications", service.listForStaff(query, status));
        model.addAttribute("total", applications.count());
        model.addAttribute("pending", applications.countByStatus(ApplicationStatus.ENVIADA)
                + applications.countByStatus(ApplicationStatus.EN_REVISION));
        model.addAttribute("observed", applications.countByStatus(ApplicationStatus.OBSERVADA));
        model.addAttribute("approved", applications.countByStatus(ApplicationStatus.APROBADA));
        model.addAttribute("query", query);
        model.addAttribute("statusFilter", status);
        model.addAttribute("statuses", ApplicationStatus.values());
        model.addAttribute("pageTitle", "Bandeja de admisiones");
        return "staff/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable String id, Model model) {
        SchoolApplication application = service.getForStaff(id);
        ReviewForm reviewForm = new ReviewForm();
        reviewForm.setVersion(application.getVersion());
        reviewForm.setStatus(application.getStatus() == ApplicationStatus.ENVIADA
                ? ApplicationStatus.EN_REVISION : application.getStatus());
        reviewForm.setNote(application.getReviewNote());
        model.addAttribute("reviewForm", reviewForm);
        return detail(model, application);
    }

    @PostMapping("/{id}/revision")
    public String review(@PathVariable String id,
                         @Valid @ModelAttribute("reviewForm") ReviewForm reviewForm,
                         BindingResult errors, Principal principal, Model model,
                         RedirectAttributes flash) {
        SchoolApplication application = service.getForStaff(id);
        if (errors.hasErrors()) {
            return detail(model, application);
        }
        try {
            service.review(id, reviewForm, principal.getName());
            flash.addFlashAttribute("success", "La revisión fue guardada y está disponible para el apoderado.");
            return "redirect:/admision/" + id;
        } catch (BusinessException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            errors.reject("review.invalid", exception.getMessage());
            return detail(model, service.getForStaff(id));
        }
    }

    private String detail(Model model, SchoolApplication application) {
        model.addAttribute("schoolApplication", application);
        model.addAttribute("history", service.history(application.getId()));
        model.addAttribute("staffView", true);
        model.addAttribute("reviewStatuses", REVIEW_STATUSES);
        model.addAttribute("pageTitle", application.getReference());
        return "application/detail";
    }
}
