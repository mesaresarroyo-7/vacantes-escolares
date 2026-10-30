package pe.edu.vacantes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import pe.edu.vacantes.model.AcademicOffer;
import pe.edu.vacantes.model.ApplicationStatus;
import pe.edu.vacantes.model.Grade;
import pe.edu.vacantes.model.Role;
import pe.edu.vacantes.model.SchoolApplication;
import pe.edu.vacantes.model.UserAccount;
import pe.edu.vacantes.dto.ApplicationForm;
import pe.edu.vacantes.dto.ReviewForm;
import pe.edu.vacantes.repository.AcademicOfferRepository;
import pe.edu.vacantes.repository.ApplicationEventRepository;
import pe.edu.vacantes.repository.AuditEventRepository;
import pe.edu.vacantes.repository.SchoolApplicationRepository;
import pe.edu.vacantes.repository.UserAccountRepository;
import pe.edu.vacantes.service.ApplicationService;
import pe.edu.vacantes.service.CaptchaService;
import pe.edu.vacantes.security.LoginAttempts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba los controles y el proceso observable con H2 aislada
 * y cuentas ficticias exclusivas de las pruebas.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PortalIntegrationTest {

    private static final String GUARDIAN = "familia@prueba.test";
    private static final String OTHER = "otra-familia@prueba.test";
    private static final String STAFF = "admision@prueba.test";
    private static final String ADMIN = "administrador@prueba.test";
    private static final String TEST_PASSWORD = "SoloParaPruebas-2027!";

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationService service;
    @Autowired CaptchaService captcha;
    @Autowired LoginAttempts loginAttempts;
    @Autowired UserAccountRepository users;
    @Autowired AcademicOfferRepository offers;
    @Autowired SchoolApplicationRepository applications;
    @Autowired ApplicationEventRepository history;
    @Autowired AuditEventRepository audit;

    private AcademicOffer offer;

    @BeforeEach
    void cleanDatabaseAndCreateFixtures() {
        loginAttempts.clear();
        history.deleteAll();
        audit.deleteAll();
        applications.deleteAll();
        offers.deleteAll();
        users.deleteAll();
        createUser(GUARDIAN, "Familia de prueba", Role.APODERADO);
        createUser(OTHER, "Otra familia", Role.APODERADO);
        createUser(STAFF, "Personal de admision", Role.ADMISION);
        createUser(ADMIN, "Administracion de prueba", Role.ADMIN);
        offer = new AcademicOffer();
        offer.setGrade(Grade.PRIMARIA_1);
        offer.setSchoolYear(2027);
        offer.setCampus("Sede de referencia");
        offer.setCapacity(25);
        offer.setActive(true);
        offer = offers.saveAndFlush(offer);
    }

    @Test
    void completeApplicationObservationCorrectionAndApprovalFlow() throws Exception {
        mvc.perform(post("/solicitudes/nueva")
                .with(user(GUARDIAN).roles("APODERADO")).with(csrf())
                .params(applicationParams("00000001", null)))
            .andExpect(status().is3xxRedirection());

        SchoolApplication created = applications
            .findByGuardianEmailIgnoreCaseOrderByCreatedAtDesc(GUARDIAN).getFirst();
        String id = created.getId();
        assertThat(created.getStatus()).isEqualTo(ApplicationStatus.ENVIADA);

        mvc.perform(post("/admision/{id}/revision", id)
                .with(user(STAFF).roles("ADMISION")).with(csrf())
                .param("status", "EN_REVISION")
                .param("version", Long.toString(created.getVersion()))
                .param("note", "Revision inicial de la solicitud."))
            .andExpect(status().is3xxRedirection());

        SchoolApplication underReview = applications.findById(id).orElseThrow();
        mvc.perform(post("/admision/{id}/revision", id)
                .with(user(STAFF).roles("ADMISION")).with(csrf())
                .param("status", "OBSERVADA")
                .param("version", Long.toString(underReview.getVersion()))
                .param("note", "Corrija el nombre del estudiante."))
            .andExpect(status().is3xxRedirection());

        SchoolApplication observed = applications.findById(id).orElseThrow();
        assertThat(observed.getStatus()).isEqualTo(ApplicationStatus.OBSERVADA);
        mvc.perform(get("/solicitudes/{id}", id)
                .with(user(GUARDIAN).roles("APODERADO")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("Corrija el nombre")));

        MultiValueMap<String, String> corrected =
            applicationParams("00000001", observed.getVersion());
        corrected.set("studentFirstNames", "Lucia Maria");
        mvc.perform(post("/solicitudes/{id}/editar", id)
                .with(user(GUARDIAN).roles("APODERADO")).with(csrf())
                .params(corrected))
            .andExpect(status().is3xxRedirection());

        SchoolApplication resent = applications.findById(id).orElseThrow();
        assertThat(resent.getStatus()).isEqualTo(ApplicationStatus.ENVIADA);
        assertThat(resent.getStudentFirstNames()).isEqualTo("Lucia Maria");

        mvc.perform(post("/admision/{id}/revision", id)
                .with(user(STAFF).roles("ADMISION")).with(csrf())
                .param("status", "APROBADA")
                .param("version", Long.toString(resent.getVersion()))
                .param("note", "Solicitud conforme para esta demostracion."))
            .andExpect(status().is3xxRedirection());

        SchoolApplication approved = applications.findById(id).orElseThrow();
        assertThat(approved.getStatus()).isEqualTo(ApplicationStatus.APROBADA);
        mvc.perform(get("/solicitudes/{id}", id)
                .with(user(GUARDIAN).roles("APODERADO")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(ApplicationStatus.APROBADA.getLabel())));
        assertThat(history.findByApplicationIdOrderByCreatedAtAsc(id)).hasSize(5);
        assertThat(audit.findTop150ByOrderByCreatedAtDesc()).isNotEmpty();
    }

    @Test
    void anotherGuardianCannotReadEditOrWithdrawTheApplication() throws Exception {
        SchoolApplication created = service.create(form("00000002"), GUARDIAN);
        String id = created.getId();
        mvc.perform(get("/solicitudes/{id}", id)
                .with(user(OTHER).roles("APODERADO")))
            .andExpect(status().isNotFound());
        mvc.perform(get("/solicitudes/{id}/editar", id)
                .with(user(OTHER).roles("APODERADO")))
            .andExpect(status().isNotFound());
        mvc.perform(post("/solicitudes/{id}/retirar", id)
                .with(user(OTHER).roles("APODERADO")).with(csrf())
                .param("version", Long.toString(created.getVersion())))
            .andExpect(status().isNotFound());
        assertThat(applications.findById(id).orElseThrow().getStatus())
            .isEqualTo(ApplicationStatus.ENVIADA);
    }

    @Test
    void rolesAndCsrfAreEnforcedByTheServer() throws Exception {
        mvc.perform(get("/admision").with(user(GUARDIAN).roles("APODERADO")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/usuarios").with(user(STAFF).roles("ADMISION")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/admin/auditoria").with(user(GUARDIAN).roles("APODERADO")))
            .andExpect(status().isForbidden());
        mvc.perform(post("/solicitudes/nueva")
                .with(user(GUARDIAN).roles("APODERADO"))
                .with(csrf().useInvalidToken())
                .params(applicationParams("00000003", null)))
            .andExpect(status().isForbidden());
        mvc.perform(post("/solicitudes/nueva")
                .with(user(GUARDIAN).roles("APODERADO"))
                .params(applicationParams("00000003", null)))
            .andExpect(status().isForbidden());
        assertThat(applications.count()).isZero();
    }

    @Test
    void invalidStudentInputIsRejectedBeforePersistence() throws Exception {
        MultiValueMap<String, String> invalid = applicationParams("documento-invalido", null);
        invalid.set("studentFirstNames", "");
        invalid.set("birthDate", "2099-01-01");
        mvc.perform(post("/solicitudes/nueva")
                .with(user(GUARDIAN).roles("APODERADO")).with(csrf())
                .params(invalid))
            .andExpect(status().isOk());
        assertThat(applications.count()).isZero();
    }

    @Test
    void duplicateActiveApplicationIsRejected() {
        service.create(form("00000004"), GUARDIAN);
        assertThatThrownBy(() -> service.create(form("00000004"), GUARDIAN))
            .isInstanceOf(RuntimeException.class);
        assertThat(applications.count()).isEqualTo(1);
    }

    @Test
    void approvingCannotExceedTheAvailableCapacity() {
        offer.setCapacity(1);
        offers.saveAndFlush(offer);
        SchoolApplication first = service.create(form("00000005"), GUARDIAN);
        SchoolApplication second = service.create(form("00000006"), OTHER);
        service.review(first.getId(), review(first, ApplicationStatus.APROBADA, "Conforme."), STAFF);
        assertThatThrownBy(() -> service.review(second.getId(),
            review(second, ApplicationStatus.APROBADA, "Conforme."), STAFF))
            .isInstanceOf(RuntimeException.class);
        assertThat(applications.countByOfferIdAndStatus(offer.getId(), ApplicationStatus.APROBADA))
            .isEqualTo(1);
        assertThat(applications.findById(second.getId()).orElseThrow().getStatus())
            .isEqualTo(ApplicationStatus.ENVIADA);
    }

    @Test
    void observationsNeedAnExplanationAndFinalDecisionsAreImmutable() {
        SchoolApplication created = service.create(form("00000007"), GUARDIAN);
        assertThatThrownBy(() -> service.review(created.getId(),
            review(created, ApplicationStatus.OBSERVADA, ""), STAFF))
            .isInstanceOf(RuntimeException.class);
        service.review(created.getId(), review(created, ApplicationStatus.APROBADA, "Conforme."), STAFF);
        SchoolApplication approved = applications.findById(created.getId()).orElseThrow();
        assertThatThrownBy(() -> service.review(approved.getId(),
            review(approved, ApplicationStatus.RECHAZADA, "Cambio posterior."), STAFF))
            .isInstanceOf(RuntimeException.class);
        ApplicationForm edit = form("00000007");
        edit.setVersion(approved.getVersion());
        assertThatThrownBy(() -> service.update(approved.getId(), edit, GUARDIAN))
            .isInstanceOf(RuntimeException.class);
        assertThat(applications.findById(approved.getId()).orElseThrow().getStatus())
            .isEqualTo(ApplicationStatus.APROBADA);
    }

    @Test
    void aStaleFormCannotOverwriteAReview() {
        SchoolApplication created = service.create(form("00000008"), GUARDIAN);
        ApplicationForm stale = form("00000008");
        stale.setVersion(created.getVersion());
        service.review(created.getId(),
            review(created, ApplicationStatus.OBSERVADA, "Revise los datos."), STAFF);
        assertThatThrownBy(() -> service.update(created.getId(), stale, GUARDIAN))
            .isInstanceOf(RuntimeException.class);
        assertThat(applications.findById(created.getId()).orElseThrow().getStatus())
            .isEqualTo(ApplicationStatus.OBSERVADA);
    }

    @Test
    void userControlledTextIsRenderedAsTextInsteadOfHtml() throws Exception {
        SchoolApplication created = service.create(form("00000009"), GUARDIAN);
        created.setGuardianMessage("<script>alert('xss')</script>");
        applications.saveAndFlush(created);
        mvc.perform(get("/solicitudes/{id}", created.getId())
                .with(user(GUARDIAN).roles("APODERADO")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("&lt;script&gt;")))
            .andExpect(content().string(not(containsString("<script>alert('xss')</script>"))));
    }

    @Test
    void publicRegistrationNeverGrantsThePostedAdministratorRole() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult page = mvc.perform(get("/registro").session(session))
            .andExpect(status().isOk()).andReturn();
        String question = (String) page.getModelAndView().getModel().get("captchaQuestion");
        mvc.perform(post("/registro").session(session).with(csrf())
                .param("fullName", "Nueva Familia")
                .param("email", "nueva@prueba.test")
                .param("phone", "999888777")
                .param("password", TEST_PASSWORD)
                .param("confirmPassword", TEST_PASSWORD)
                .param("captchaAnswer", answer(question))
                .param("role", "ADMIN"))
            .andExpect(status().is3xxRedirection());
        UserAccount registered = users.findByEmailIgnoreCase("nueva@prueba.test").orElseThrow();
        assertThat(registered.getRole()).isEqualTo(Role.APODERADO);
        assertThat(registered.getPasswordHash()).contains("$argon2id$");
        assertThat(encoder.matches(TEST_PASSWORD, registered.getPasswordHash())).isTrue();
    }

    @Test
    void captchaChallengeIsConsumedAndPasswordsUseArgon2() {
        MockHttpSession session = new MockHttpSession();
        ExtendedModelMap model = new ExtendedModelMap();
        captcha.prepare(session, model);
        String result = answer((String) model.get("captchaQuestion"));
        assertThat(captcha.verify(session, result, null)).isTrue();
        assertThat(captcha.verify(session, result, null)).isFalse();

        String hash = encoder.encode(TEST_PASSWORD);
        assertThat(hash).contains("$argon2id$").isNotEqualTo(TEST_PASSWORD);
        assertThat(encoder.matches(TEST_PASSWORD, hash)).isTrue();
        assertThat(encoder.matches("Una-contrasena-distinta", hash)).isFalse();
    }

    @Test
    void realFormLoginCreatesASessionAndLogoutRevokesIt() throws Exception {
        MvcResult login = mvc.perform(post("/login").with(csrf())
                .param("username", GUARDIAN).param("password", TEST_PASSWORD))
            .andExpect(status().is3xxRedirection()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/panel").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/solicitudes"));
        mvc.perform(post("/logout").session(session).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertThat(session.isInvalid()).isTrue();
        mvc.perform(get("/solicitudes"))
            .andExpect(status().is3xxRedirection());
    }

    @Test
    void repeatedFailedLoginsBlockEvenTheCorrectPassword() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mvc.perform(post("/login").with(csrf())
                    .param("username", GUARDIAN).param("password", "ClaveIncorrecta-2027!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
        }
        mvc.perform(post("/login").with(csrf())
                .param("username", GUARDIAN).param("password", TEST_PASSWORD))
            .andExpect(status().isTooManyRequests());
    }

    @Test
    void injectionLikeUsernameAndDisabledAccountCannotAuthenticate() throws Exception {
        mvc.perform(post("/login").with(csrf())
                .param("username", "' OR 1=1 --").param("password", TEST_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
        UserAccount guardian = users.findByEmailIgnoreCase(GUARDIAN).orElseThrow();
        guardian.setEnabled(false);
        users.saveAndFlush(guardian);
        mvc.perform(post("/login").with(csrf())
                .param("username", GUARDIAN).param("password", TEST_PASSWORD))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error"));
        assertThat(users.count()).isEqualTo(4);
    }

    @Test
    void administratorCanDisableOnlyGuardianAccounts() throws Exception {
        UserAccount guardian = users.findByEmailIgnoreCase(GUARDIAN).orElseThrow();
        mvc.perform(post("/admin/usuarios/{id}/estado", guardian.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()).param("enabled", "false"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/admin/usuarios"));
        assertThat(users.findById(guardian.getId()).orElseThrow().isEnabled()).isFalse();
        UserAccount staff = users.findByEmailIgnoreCase(STAFF).orElseThrow();
        MvcResult blocked = mvc.perform(post("/admin/usuarios/{id}/estado", staff.getId())
                .with(user(ADMIN).roles("ADMIN")).with(csrf()).param("enabled", "false"))
            .andReturn();
        assertThat(blocked.getResponse().getStatus()).isIn(302, 303, 400, 403);
        assertThat(users.findById(staff.getId()).orElseThrow().isEnabled()).isTrue();
    }

    private UserAccount createUser(String email, String name, Role role) {
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setFullName(name);
        account.setPhone("999888777");
        account.setPasswordHash(encoder.encode(TEST_PASSWORD));
        account.setRole(role);
        account.setEnabled(true);
        return users.saveAndFlush(account);
    }

    private ApplicationForm form(String document) {
        ApplicationForm form = new ApplicationForm();
        form.setOfferId(offer.getId());
        form.setStudentFirstNames("Lucia");
        form.setStudentLastNames("Torres Perez");
        form.setStudentDocument(document);
        form.setBirthDate(LocalDate.of(2018, 2, 10));
        form.setGuardianMessage("Solicitud academica de demostracion.");
        return form;
    }

    private MultiValueMap<String, String> applicationParams(String document, Long version) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("offerId", offer.getId().toString());
        params.add("studentFirstNames", "Lucia");
        params.add("studentLastNames", "Torres Perez");
        params.add("studentDocument", document);
        params.add("birthDate", "2018-02-10");
        params.add("guardianMessage", "Solicitud academica de demostracion.");
        if (version != null) params.add("version", version.toString());
        return params;
    }

    private ReviewForm review(SchoolApplication application, ApplicationStatus status, String note) {
        ReviewForm form = new ReviewForm();
        form.setStatus(status);
        form.setNote(note);
        form.setVersion(application.getVersion());
        return form;
    }

    private static String answer(String question) {
        List<Integer> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(question);
        while (matcher.find()) values.add(Integer.parseInt(matcher.group()));
        if (values.size() != 2) throw new IllegalArgumentException("Pregunta CAPTCHA inesperada");
        return Integer.toString(values.get(0) + values.get(1));
    }
}
