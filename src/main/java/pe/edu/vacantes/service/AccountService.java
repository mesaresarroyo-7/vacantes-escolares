package pe.edu.vacantes.service;

import jakarta.validation.Validator;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.vacantes.domain.Role;
import pe.edu.vacantes.domain.UserAccount;
import pe.edu.vacantes.form.RegistrationForm;
import pe.edu.vacantes.repository.UserAccountRepository;

@Service
public class AccountService {
    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final Validator validator;
    private final AuditService audit;

    public AccountService(UserAccountRepository users, PasswordEncoder encoder,
                          Validator validator, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional
    public UserAccount register(RegistrationForm form) {
        if (form == null || !validator.validate(form).isEmpty()) {
            throw new IllegalArgumentException("Revisa los datos de registro.");
        }
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("Las contraseñas no coinciden.");
        }
        String email = form.getEmail().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Ya existe una cuenta con ese correo.");
        }
        UserAccount user = new UserAccount();
        user.setEmail(email);
        user.setFullName(form.getFullName().trim());
        user.setPhone(form.getPhone().trim());
        user.setPasswordHash(encoder.encode(form.getPassword()));
        // Public registration never accepts a role from the request.
        user.setRole(Role.APODERADO);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        try {
            user = users.saveAndFlush(user);
        } catch (DataIntegrityViolationException duplicate) {
            throw new IllegalArgumentException("No se pudo crear la cuenta. Revisa el correo e inténtalo nuevamente.");
        }
        audit.record(user.getEmail(), "CUENTA_REGISTRADA", user.getId().toString(),
                "Se creó una cuenta de apoderado.");
        return user;
    }
}
