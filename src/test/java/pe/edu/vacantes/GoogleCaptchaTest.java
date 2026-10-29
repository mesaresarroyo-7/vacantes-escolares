package pe.edu.vacantes;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import pe.edu.vacantes.service.CaptchaService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Provider responses are simulated; these tests never connect to Google. */
class GoogleCaptchaTest {
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final String FAKE_SITE_KEY = "unit-test-site-key-not-a-real-google-key";
    private static final String FAKE_SECRET_KEY = "unit-test-secret-not-a-real-google-key";
    private static final String FAKE_TOKEN = "unit-test-response-token";

    private CaptchaService captcha;
    private MockRestServiceServer google;

    @BeforeEach
    void simulateGoogleTransport() {
        // Deliberately omit app.captcha.mode: the default must be Google.
        MockEnvironment environment = new MockEnvironment()
                .withProperty("app.captcha.site-key", FAKE_SITE_KEY)
                .withProperty("app.captcha.secret-key", FAKE_SECRET_KEY)
                .withProperty("app.captcha.allowed-hostnames", "localhost,127.0.0.1");
        captcha = new CaptchaService(environment);
        RestClient.Builder client = RestClient.builder();
        google = MockRestServiceServer.bindTo(client).build();
        ReflectionTestUtils.setField(captcha, "google", client.build());
    }

    @Test
    void missingKeysStopStartupInsteadOfFallingBackToArithmetic() {
        assertThrows(IllegalStateException.class, () -> new CaptchaService(new MockEnvironment()));
        assertThrows(IllegalStateException.class, () -> new CaptchaService(new MockEnvironment()
                .withProperty("app.captcha.site-key", FAKE_SITE_KEY)));
        google.verify();
    }

    @Test
    void defaultConfigurationPreparesGoogleWidgetWithoutArithmeticChallenge() {
        ExtendedModelMap page = new ExtendedModelMap();
        captcha.prepare(new MockHttpSession(), page);
        assertEquals("google", page.get("captchaMode"));
        assertEquals(FAKE_SITE_KEY, page.get("recaptchaSiteKey"));
        assertFalse(page.containsAttribute("captchaQuestion"));
        google.verify();
    }

    @Test
    void acceptsSuccessfulFreshTokenForAllowedHostAndPostsTokenToProvider() {
        LinkedMultiValueMap<String, String> postedForm = new LinkedMultiValueMap<>();
        postedForm.add("secret", FAKE_SECRET_KEY);
        postedForm.add("response", FAKE_TOKEN);
        google.expect(requestTo(VERIFY_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(postedForm))
                .andRespond(withSuccess(response(true, "localhost", Instant.now().minusSeconds(5)),
                        MediaType.APPLICATION_JSON));

        assertTrue(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void rejectsSuccessfulTokenIssuedForAnotherHost() {
        reply(response(true, "unrelated.example", Instant.now().minusSeconds(5)));
        assertFalse(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void rejectsTokenOlderThanTwoMinutes() {
        reply(response(true, "127.0.0.1", Instant.now().minusSeconds(180)));
        assertFalse(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void rejectsTokenWithImplausibleFutureTimestamp() {
        reply(response(true, "localhost", Instant.now().plusSeconds(90)));
        assertFalse(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void rejectsTokenWhenProviderDeniesIt() {
        reply(response(false, "localhost", Instant.now().minusSeconds(5)));
        assertFalse(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void rejectsMalformedProviderTimestamp() {
        reply("{\"success\":true,\"hostname\":\"localhost\",\"challenge_ts\":\"not-a-date\"}");
        assertFalse(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void providerFailureDoesNotGrantAccess() {
        google.expect(requestTo(VERIFY_URL)).andRespond(withServerError());
        assertFalse(captcha.verify(new MockHttpSession(), null, FAKE_TOKEN));
        google.verify();
    }

    @Test
    void emptyOrOversizedTokensAreRejectedWithoutContactingGoogle() {
        // No expected requests: any network attempt fails this test.
        assertFalse(captcha.verify(new MockHttpSession(), null, null));
        assertFalse(captcha.verify(new MockHttpSession(), null, ""));
        assertFalse(captcha.verify(new MockHttpSession(), null, "   "));
        assertFalse(captcha.verify(new MockHttpSession(), null, "x".repeat(4097)));
        google.verify();
    }

    private void reply(String body) {
        google.expect(requestTo(VERIFY_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String response(boolean success, String hostname, Instant timestamp) {
        return "{\"success\":" + success + ",\"hostname\":\"" + hostname
                + "\",\"challenge_ts\":\"" + timestamp + "\"}";
    }
}
