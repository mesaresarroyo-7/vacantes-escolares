package pe.edu.vacantes.service;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class CaptchaService {
    private static final String SESSION_KEY = CaptchaService.class.getName() + ".challenge";
    private final SecureRandom random = new SecureRandom();
    private final String mode;
    private final String siteKey;
    private final String secretKey;
    private final Set<String> allowedHostnames;
    private final RestClient google;

    public CaptchaService(Environment environment) {
        mode = environment.getProperty("app.captcha.mode", "google").trim().toLowerCase(Locale.ROOT);
        siteKey = environment.getProperty("app.captcha.site-key", "");
        secretKey = environment.getProperty("app.captcha.secret-key", "");
        allowedHostnames = Arrays.stream(environment.getProperty(
                        "app.captcha.allowed-hostnames", "localhost,127.0.0.1").split(","))
                .map(String::trim).map(value -> value.toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        if (!mode.equals("local") && !mode.equals("google")) {
            throw new IllegalStateException("El modo CAPTCHA debe ser local o google.");
        }
        if (mode.equals("google") && (siteKey.isBlank() || secretKey.isBlank() || allowedHostnames.isEmpty())) {
            throw new IllegalStateException("Configura las claves y los nombres de host de reCAPTCHA.");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(4000);
        google = RestClient.builder().requestFactory(factory).build();
    }

    public void prepare(HttpSession session, Model model) {
        model.addAttribute("captchaMode", mode);
        model.addAttribute("recaptchaSiteKey", siteKey);
        if (mode.equals("local")) {
            int first = 2 + random.nextInt(8);
            int second = 2 + random.nextInt(8);
            session.setAttribute(SESSION_KEY, new Challenge(
                    Integer.toString(first + second), Instant.now().plus(Duration.ofMinutes(5))));
            model.addAttribute("captchaQuestion", first + " + " + second + " = ?");
        }
    }

    public boolean verify(HttpSession session, String answer, String googleToken) {
        if (mode.equals("google")) return verifyGoogle(googleToken);
        Object stored = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        if (!(stored instanceof Challenge challenge)) return false;
        return challenge.used.compareAndSet(false, true)
                && Instant.now().isBefore(challenge.expiresAt)
                && answer != null && answer.length() <= 20
                && challenge.answer.equals(answer.trim());
    }

    private boolean verifyGoogle(String token) {
        if (token == null || token.isBlank() || token.length() > 4096) return false;
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secretKey);
        form.add("response", token);
        try {
            JsonNode reply = google.post().uri("https://www.google.com/recaptcha/api/siteverify")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(JsonNode.class);
            if (reply == null || !reply.path("success").asBoolean(false)) return false;
            String host = reply.path("hostname").asText("").toLowerCase(Locale.ROOT);
            if (!allowedHostnames.contains(host)) return false;
            Instant timestamp = Instant.parse(reply.path("challenge_ts").asText(""));
            Instant now = Instant.now();
            return !timestamp.isBefore(now.minusSeconds(120))
                    && !timestamp.isAfter(now.plusSeconds(30));
        } catch (RestClientException | java.time.format.DateTimeParseException exception) {
            // Fail closed. Provider responses and secrets never go to the page or log.
            return false;
        }
    }

    private static final class Challenge implements Serializable {
        private static final long serialVersionUID = 1L;
        private final String answer;
        private final Instant expiresAt;
        private final AtomicBoolean used = new AtomicBoolean();
        private Challenge(String answer, Instant expiresAt) {
            this.answer = answer;
            this.expiresAt = expiresAt;
        }
    }
}
