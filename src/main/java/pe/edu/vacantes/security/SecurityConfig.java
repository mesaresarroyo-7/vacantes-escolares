package pe.edu.vacantes.security;

import jakarta.servlet.DispatcherType;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import pe.edu.vacantes.model.UserAccount;
import pe.edu.vacantes.repository.UserAccountRepository;
import pe.edu.vacantes.service.AuditService;

@Configuration
public class SecurityConfig {
    private static final String CSP =
            "default-src 'self'; script-src 'self' https://www.google.com/recaptcha/ https://www.gstatic.com/recaptcha/; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data: https://www.gstatic.com/recaptcha/; "
            + "frame-src https://www.google.com/recaptcha/ https://recaptcha.google.com/recaptcha/; "
            + "connect-src 'self' https://www.google.com/recaptcha/; "
            + "font-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    public UserDetailsService userDetailsService(UserAccountRepository users) {
        return username -> {
            if (username == null || username.length() > 190) {
                throw new UsernameNotFoundException("Credenciales inválidas.");
            }
            UserAccount account = users.findByEmailIgnoreCase(username.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException("Credenciales inválidas."));
            return User.withUsername(account.getEmail()).password(account.getPasswordHash())
                    .roles(account.getRole().name()).disabled(!account.isEnabled()).build();
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserAccountRepository users,
                                          LoginAttempts attempts, AuditService audit) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                .requestMatchers("/", "/login", "/registro", "/error", "/error/**",
                        "/css/**", "/js/**", "/favicon.ico").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/admision", "/admision/**").hasAnyRole("ADMISION", "ADMIN")
                .requestMatchers("/solicitudes", "/solicitudes/**").hasRole("APODERADO")
                .anyRequest().authenticated())
            .csrf(Customizer.withDefaults())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(login -> login.loginPage("/login").loginProcessingUrl("/login")
                .usernameParameter("username").passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    attempts.success(request.getRemoteAddr(), authentication.getName());
                    audit.record(authentication.getName(), "LOGIN_CORRECTO", null, "Se inició una sesión.");
                    response.sendRedirect(request.getContextPath() + "/panel");
                })
                .failureHandler((request, response, exception) -> {
                    attempts.failure(request.getRemoteAddr(), request.getParameter("username"));
                    audit.record("anonimo", "LOGIN_FALLIDO", null, "Intento no válido de acceso.");
                    response.sendRedirect(request.getContextPath() + "/login?error");
                }).permitAll())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(fixation -> fixation.changeSessionId()))
            .logout(logout -> logout.logoutUrl("/logout").invalidateHttpSession(true)
                .clearAuthentication(true).deleteCookies("JSESSIONID")
                .logoutSuccessHandler((request, response, authentication) -> {
                    if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
                        audit.record(authentication.getName(), "LOGOUT", null, "Se cerró una sesión.");
                    }
                    response.sendRedirect(request.getContextPath() + "/login?logout");
                }))
            .exceptionHandling(errors -> errors.accessDeniedHandler(
                (request, response, exception) -> response.sendError(403)))
            .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(CSP))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .addFilterBefore(new AccountProtectionFilter(users, attempts),
                    UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
