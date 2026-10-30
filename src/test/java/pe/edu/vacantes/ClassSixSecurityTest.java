package pe.edu.vacantes;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import pe.edu.vacantes.model.AcademicOffer;
import pe.edu.vacantes.model.ApplicationStatus;
import pe.edu.vacantes.model.AuditEvent;
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
import pe.edu.vacantes.service.AuditService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/** Verifica rechazo de negocio, persistencia de auditoría y salida segura a logs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClassSixSecurityTest {
    private static final String GUARDIAN = "familia-clase6@prueba.test";
    private static final String OTHER = "otra-clase6@prueba.test";
    private static final String STAFF = "admision-clase6@prueba.test";
    private static final String PASSWORD = "SoloPruebasClase6-2027!";
    private static final String REMOTE_IP = "192.0.2.40";

    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired ApplicationService service;
    @Autowired AuditService auditService;
    @Autowired UserAccountRepository users;
    @Autowired AcademicOfferRepository offers;
    @Autowired SchoolApplicationRepository applications;
    @Autowired ApplicationEventRepository history;
    @Autowired AuditEventRepository audit;
    @Autowired PlatformTransactionManager transactions;
    private AcademicOffer offer;

    @BeforeEach
    void fixtures() {
        history.deleteAll();
        audit.deleteAll();
        applications.deleteAll();
        offers.deleteAll();
        users.deleteAll();
        createUser(GUARDIAN, Role.APODERADO);
        createUser(OTHER, Role.APODERADO);
        createUser(STAFF, Role.ADMISION);
        offer = new AcademicOffer();
        offer.setGrade(Grade.PRIMARIA_1);
        offer.setSchoolYear(2027);
        offer.setCampus("Sede de referencia");
        offer.setCapacity(1);
        offer.setActive(true);
        offer = offers.saveAndFlush(offer);
    }

    @Test
    void duplicateRequestIsFriendlyAndAuditedAfterTheBusinessTransactionRollsBack() throws Exception {
        service.create(form("87654321"), GUARDIAN);

        mvc.perform(post("/solicitudes/nueva")
                .with(user(GUARDIAN).roles("APODERADO")).with(csrf())
                .with(request -> { request.setRemoteAddr(REMOTE_IP); return request; })
                .header("X-Forwarded-For", "203.0.113.99")
                .params(params("87654321")))
            .andExpect(status().isBadRequest())
            .andExpect(view().name("error/error"))
            .andExpect(content().string(containsString("Ya tienes una solicitud activa")))
            .andExpect(content().string(not(containsString("87654321"))))
            .andExpect(content().string(not(containsString("DuplicateApplicationException"))));

        assertThat(applications.count()).isEqualTo(1);
        AuditEvent rejected = rejection("SOLICITUD_DUPLICADA");
        assertThat(rejected.getActor()).isEqualTo(GUARDIAN);
        assertThat(rejected.getLevel()).isEqualTo("WARN");
        assertThat(rejected.getOriginIp()).isEqualTo(REMOTE_IP).isNotEqualTo("203.0.113.99");
        assertThat(rejected.getDetail()).doesNotContain("87654321");
    }

    @Test
    void exhaustedVacanciesCannotApproveAnotherApplicationAndTheRejectionSurvivesRollback() throws Exception {
        SchoolApplication first = service.create(form("11111111"), GUARDIAN);
        SchoolApplication second = service.create(form("22222222"), OTHER);
        ReviewForm accepted = new ReviewForm();
        accepted.setStatus(ApplicationStatus.APROBADA);
        accepted.setVersion(first.getVersion());
        accepted.setNote("Conforme para esta demostración.");
        service.review(first.getId(), accepted, STAFF);

        mvc.perform(post("/admision/{id}/revision", second.getId())
                .with(user(STAFF).roles("ADMISION")).with(csrf())
                .with(request -> { request.setRemoteAddr(REMOTE_IP); return request; })
                .param("status", "APROBADA")
                .param("version", Long.toString(second.getVersion()))
                .param("note", "Intento de aprobar después de completar el aforo."))
            .andExpect(status().isBadRequest())
            .andExpect(view().name("error/error"))
            .andExpect(content().string(containsString("No quedan vacantes disponibles")))
            .andExpect(content().string(not(containsString("NoVacanciesException"))));

        assertThat(applications.countByOfferIdAndStatus(offer.getId(), ApplicationStatus.APROBADA))
            .isEqualTo(1);
        assertThat(applications.findById(second.getId()).orElseThrow().getStatus())
            .isEqualTo(ApplicationStatus.ENVIADA);
        AuditEvent rejected = rejection("SIN_VACANTES");
        assertThat(rejected.getLevel()).isEqualTo("WARN");
        assertThat(rejected.getOriginIp()).isEqualTo(REMOTE_IP);
    }

    @Test
    void unknownAndOtherOwnersApplicationRemainIndistinguishable() throws Exception {
        SchoolApplication created = service.create(form("33333333"), GUARDIAN);
        MvcResult forbiddenId = mvc.perform(get("/solicitudes/{id}", created.getId())
                .with(user(OTHER).roles("APODERADO")))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/error"))
            .andExpect(content().string(not(containsString(created.getId()))))
            .andReturn();
        MvcResult unknownId = mvc.perform(get("/solicitudes/{id}", UUID.randomUUID())
                .with(user(OTHER).roles("APODERADO")))
            .andExpect(status().isNotFound())
            .andExpect(view().name("error/error"))
            .andReturn();

        assertThat(forbiddenId.getModelAndView().getModel().get("message"))
            .isEqualTo(unknownId.getModelAndView().getModel().get("message"));
        List<AuditEvent> rejections = audit.findTop150ByOrderByCreatedAtDesc().stream()
            .filter(event -> event.getAction().equals("NEGOCIO_RECHAZADO"))
            .filter(event -> event.getTarget().equals("SOLICITUD_NO_ENCONTRADA"))
            .toList();
        assertThat(rejections).hasSize(2);
        assertThat(rejections).allSatisfy(event -> {
            assertThat(event.getDetail()).doesNotContain(created.getId(), "33333333");
            assertThat(event.getLevel()).isEqualTo("WARN");
        });
    }

    @Test
    void auditDatabaseAndLoggerDoNotLeakSecretsDocumentsOrForgedLines() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuditService.class);
        ListAppender<ILoggingEvent> capture = new ListAppender<>();
        capture.setContext(logger.getLoggerContext());
        capture.start();
        logger.addAppender(capture);
        try {
            auditService.record("familia@prueba.test\r\nFORGED_ACTOR", "CONSULTA\r\nFORGED_ACTION",
                "token=NoPublicarToken", "password=NoPublicarClave secret=NoPublicarSecreto"
                    + " DNI 87654321\r\nFORGED_DETAIL", "WARN", REMOTE_IP);

            AuditEvent stored = audit.findTop150ByOrderByCreatedAtDesc().getFirst();
            String storedText = String.join(" ", stored.getActor(), stored.getAction(),
                stored.getTarget(), stored.getDetail());
            assertThat(storedText).doesNotContain("\r", "\n", "NoPublicarClave", "NoPublicarSecreto",
                "NoPublicarToken", "87654321");
            assertThat(stored.getDetail()).contains("[omitido]", "[documento omitido]");
            assertThat(capture.list).isNotEmpty();
            assertThat(capture.list).anySatisfy(event -> {
                assertThat(event.getLevel().toString()).isEqualTo("WARN");
                assertThat(event.getFormattedMessage()).contains("CONSULTA")
                    .doesNotContain("\r", "\n", "NoPublicarClave", "NoPublicarSecreto",
                        "NoPublicarToken", "87654321");
            });
        } finally {
            logger.detachAppender(capture);
            capture.stop();
        }
    }

    @Test
    void aRolledBackOuterTransactionNeverLeavesASuccessfulCreationAudit() {
        TransactionTemplate outerTransaction = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> outerTransaction.executeWithoutResult(status -> {
            service.create(form("44444444"), GUARDIAN);
            throw new IllegalStateException("Rollback controlado después de crear la solicitud.");
        })).isInstanceOf(IllegalStateException.class)
            .hasMessage("Rollback controlado después de crear la solicitud.");

        assertThat(applications.count()).isZero();
        assertThat(history.count()).isZero();
        List<AuditEvent> recorded = audit.findTop150ByOrderByCreatedAtDesc();
        assertThat(recorded).extracting(AuditEvent::getAction).doesNotContain("SOLICITUD_CREADA");
        assertThat(recorded).anySatisfy(event -> {
            assertThat(event.getAction()).contains("INTENTO");
            assertThat(event.getActor()).isEqualTo(GUARDIAN);
        });
    }

    private AuditEvent rejection(String code) {
        return audit.findTop150ByOrderByCreatedAtDesc().stream()
            .filter(event -> "NEGOCIO_RECHAZADO".equals(event.getAction()))
            .filter(event -> code.equals(event.getTarget()))
            .findFirst().orElseThrow();
    }

    private void createUser(String email, Role role) {
        UserAccount account = new UserAccount();
        account.setEmail(email);
        account.setFullName("Cuenta ficticia de clase 6");
        account.setPhone("999888777");
        account.setPasswordHash(encoder.encode(PASSWORD));
        account.setRole(role);
        account.setEnabled(true);
        users.saveAndFlush(account);
    }

    private ApplicationForm form(String document) {
        ApplicationForm form = new ApplicationForm();
        form.setOfferId(offer.getId());
        form.setStudentFirstNames("Lucia");
        form.setStudentLastNames("Torres Demo");
        form.setStudentDocument(document);
        form.setBirthDate(LocalDate.of(2018, 2, 10));
        return form;
    }

    private MultiValueMap<String, String> params(String document) {
        MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("offerId", offer.getId().toString());
        parameters.add("studentFirstNames", "Lucia");
        parameters.add("studentLastNames", "Torres Demo");
        parameters.add("studentDocument", document);
        parameters.add("birthDate", "2018-02-10");
        return parameters;
    }
}
