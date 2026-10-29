package pe.edu.vacantes.config;

import pe.edu.vacantes.domain.*;
import pe.edu.vacantes.form.*;
import pe.edu.vacantes.repository.*;
import pe.edu.vacantes.service.ApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Arrays;

@Component
@ConditionalOnProperty(name="app.seed.enabled",havingValue="true",matchIfMissing=true)
public class DemoSeeder implements ApplicationRunner {
    private final UserAccountRepository users;
    private final AcademicOfferRepository offers;
    private final SchoolApplicationRepository applications;
    private final PasswordEncoder encoder;
    private final ApplicationService service;
    @Value("${app.seed.admin-password:}") private String adminPassword;
    @Value("${app.seed.admissions-password:}") private String admissionsPassword;
    @Value("${app.seed.guardian-password:}") private String guardianPassword;
    @Value("${app.seed.other-password:}") private String otherPassword;
    public DemoSeeder(UserAccountRepository users,AcademicOfferRepository offers,
                      SchoolApplicationRepository applications,PasswordEncoder encoder,ApplicationService service) {
        this.users=users; this.offers=offers; this.applications=applications; this.encoder=encoder; this.service=service;
    }
    @Override @Transactional
    public void run(ApplicationArguments arguments) {
        account("admin@colegio.test","Administración del portal",Role.ADMIN,adminPassword);
        account("admisiones@colegio.test","Equipo de admisiones",Role.ADMISION,admissionsPassword);
        account("apoderado@colegio.test","María Torres Demo",Role.APODERADO,guardianPassword);
        account("otro@colegio.test","Carlos Rojas Demo",Role.APODERADO,otherPassword);
        if(offers.count()==0) {
            Arrays.stream(Grade.values()).forEach(grade->{
                AcademicOffer offer=new AcademicOffer(); offer.setGrade(grade); offers.save(offer);
            });
            offers.flush();
        }
        if(applications.count()==0) {
            AcademicOffer primary=offers.findByActiveTrueOrderByIdAsc().stream()
                    .filter(o->o.getGrade()==Grade.PRIMARIA_1).findFirst().orElseThrow();
            SchoolApplication observed=service.create(form(primary,"Lucía","Torres Demo","00000001",LocalDate.of(2020,5,10)),"apoderado@colegio.test");
            ReviewForm review=new ReviewForm(); review.setStatus(ApplicationStatus.OBSERVADA);
            review.setVersion(observed.getVersion()); review.setNote("Revisa los apellidos del estudiante antes de reenviar la solicitud.");
            service.review(observed.getId(),review,"admisiones@colegio.test");
            AcademicOffer secondary=offers.findByActiveTrueOrderByIdAsc().stream()
                    .filter(o->o.getGrade()==Grade.SECUNDARIA_1).findFirst().orElseThrow();
            service.create(form(secondary,"Mateo","Torres Demo","00000002",LocalDate.of(2014,7,12)),"apoderado@colegio.test");
            service.create(form(primary,"Sofía","Rojas Demo","00000003",LocalDate.of(2020,8,20)),"otro@colegio.test");
        }
    }
    private void account(String email,String name,Role role,String password) {
        if(users.existsByEmailIgnoreCase(email)) return;
        if(password==null || password.length()<10)
            throw new IllegalStateException("Configura las variables DEMO_* con contraseñas de al menos 10 caracteres. Consulta README.md.");
        UserAccount user=new UserAccount(); user.setEmail(email); user.setFullName(name);
        user.setPhone("900000000"); user.setPasswordHash(encoder.encode(password)); user.setRole(role);
        users.saveAndFlush(user);
    }
    private ApplicationForm form(AcademicOffer offer,String names,String surnames,String document,LocalDate birth) {
        ApplicationForm form=new ApplicationForm(); form.setOfferId(offer.getId());
        form.setStudentFirstNames(names); form.setStudentLastNames(surnames);
        form.setStudentDocument(document); form.setBirthDate(birth);
        form.setGuardianMessage("Solicitud de ejemplo con datos ficticios para la demostración académica.");
        return form;
    }
}
