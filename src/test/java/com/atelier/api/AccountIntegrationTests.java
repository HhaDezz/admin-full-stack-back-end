package com.atelier.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atelier.api.security.TotpService;
import com.atelier.api.service.MailService;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/** Sign-up, email confirmation, password reset, two-step sign-in, devices and notifications. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountIntegrationTests {

    static final String CHROME_MAC = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    static final String FIREFOX_WIN = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0";
    static final String SAFARI_IPHONE = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1";

    @Autowired
    MockMvc mvc;
    @Autowired
    MailService mail;
    @Autowired
    TotpService totp;

    /* ── helpers ── */

    static String unique() {
        return "a" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    ResultActions postJson(String url, String json, String token, String ua) throws Exception {
        var req = post(url).contentType(MediaType.APPLICATION_JSON).content(json).header("User-Agent", ua == null ? CHROME_MAC : ua);
        if (token != null) req = req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    ResultActions getAs(String url, String token) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    String loginBody(String email, String password, String ua) throws Exception {
        return body(postJson("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null, ua)
                .andExpect(status().isOk()));
    }

    String login(String email, String password, String ua) throws Exception {
        return JsonPath.read(loginBody(email, password, ua), "$.data.token");
    }

    /** The token from the newest email sent to this address whose link contains the path. */
    String tokenFromMail(String to, String path) {
        Pattern p = Pattern.compile(Pattern.quote(path) + "\\?token=([A-Za-z0-9_-]+)");
        for (MailService.Mail m : mail.recent()) {
            if (!m.to().equals(to)) continue;
            Matcher matcher = p.matcher(m.text());
            if (matcher.find()) return matcher.group(1);
        }
        throw new AssertionError("No email to " + to + " with " + path);
    }

    /** Registers and confirms an account; returns its email. */
    String verifiedAccount(String password) throws Exception {
        String email = unique();
        postJson("/auth/register", "{\"name\":\"Ada\",\"email\":\"" + email + "\",\"password\":\"" + password + "\"}", null, null)
                .andExpect(status().isCreated());
        postJson("/auth/verify-email", "{\"token\":\"" + tokenFromMail(email, "/verify-email") + "\"}", null, null)
                .andExpect(status().isOk());
        return email;
    }

    /* ── sign-up and email confirmation ── */

    @Test
    void optionsArePublic() throws Exception {
        mvc.perform(get("/auth/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.registrationEnabled").value(true))
                .andExpect(jsonPath("$.data.requireEmailVerification").value(true));
    }

    @Test
    void signUpRequiresEmailConfirmationAndLinksWorkOnce() throws Exception {
        String email = unique();
        postJson("/auth/register", "{\"name\":\"Grace Hopper\",\"email\":\"" + email.toUpperCase() + "\",\"password\":\"Password123!\"}", null, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.verificationRequired").value(true));

        postJson("/auth/register", "{\"name\":\"Again\",\"email\":\"" + email + "\",\"password\":\"Password123!\"}", null, null)
                .andExpect(status().isConflict()).andExpect(jsonPath("$.data.email").exists());

        postJson("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"Password123!\"}", null, null)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.data.code").value("EMAIL_NOT_VERIFIED"));

        // Resending retires the first link.
        String first = tokenFromMail(email, "/verify-email");
        postJson("/auth/resend-verification", "{\"email\":\"" + email + "\"}", null, null).andExpect(status().isOk());
        String second = tokenFromMail(email, "/verify-email");
        assertThat(second).isNotEqualTo(first);
        postJson("/auth/verify-email", "{\"token\":\"" + first + "\"}", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value("LINK_INVALID"));

        postJson("/auth/verify-email", "{\"token\":\"" + second + "\"}", null, null).andExpect(status().isOk());
        postJson("/auth/verify-email", "{\"token\":\"" + second + "\"}", null, null).andExpect(status().isBadRequest());

        String me = body(getAs("/me", login(email, "Password123!", null)).andExpect(status().isOk()));
        assertThat((Boolean) JsonPath.read(me, "$.data.emailVerified")).isTrue();
        assertThat((List<String>) JsonPath.read(me, "$.data.roles")).containsExactly("USER");
    }

    @Test
    void unknownEmailsGetTheSameAnswer() throws Exception {
        String nobody = unique();
        postJson("/auth/forgot-password", "{\"email\":\"" + nobody + "\"}", null, null).andExpect(status().isOk());
        postJson("/auth/resend-verification", "{\"email\":\"" + nobody + "\"}", null, null).andExpect(status().isOk());
        assertThat(mail.recent()).noneMatch(m -> m.to().equals(nobody));
    }

    /* ── password reset ── */

    @Test
    void passwordResetSignsOutEveryDevice() throws Exception {
        String email = verifiedAccount("OldPassword1!");
        String oldToken = login(email, "OldPassword1!", null);

        postJson("/auth/forgot-password", "{\"email\":\"" + email + "\"}", null, null).andExpect(status().isOk());
        String link = tokenFromMail(email, "/reset-password");
        postJson("/auth/reset-password/check", "{\"token\":\"" + link + "\"}", null, null).andExpect(status().isOk());
        postJson("/auth/reset-password", "{\"token\":\"" + link + "\",\"password\":\"short\"}", null, null).andExpect(status().isBadRequest());
        postJson("/auth/reset-password", "{\"token\":\"" + link + "\",\"password\":\"NewPassword1!\"}", null, null).andExpect(status().isOk());

        getAs("/me", oldToken).andExpect(status().isUnauthorized());
        postJson("/auth/reset-password", "{\"token\":\"" + link + "\",\"password\":\"Another1!\"}", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value("LINK_INVALID"));
        postJson("/auth/login", "{\"email\":\"" + email + "\",\"password\":\"OldPassword1!\"}", null, null).andExpect(status().isUnauthorized());
        String fresh = login(email, "NewPassword1!", null);
        getAs("/me/notifications", fresh).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type=='PASSWORD_CHANGED')]").exists());
    }

    /* ── two-step sign-in ── */

    @Test
    void twoStepSignInWithAppCodesAndRecoveryCodes() throws Exception {
        String email = verifiedAccount("Password123!");
        String token = login(email, "Password123!", null);

        String setup = body(postJson("/me/2fa/setup", "{}", token, null).andExpect(status().isOk()));
        String secret = JsonPath.read(setup, "$.data.secret");
        assertThat((String) JsonPath.read(setup, "$.data.otpauthUrl")).startsWith("otpauth://totp/").contains("secret=" + secret);

        postJson("/me/2fa/enable", "{\"code\":\"000000\"}", token, null).andExpect(status().isBadRequest());
        long step = TotpService.currentStep();
        String codes = body(postJson("/me/2fa/enable", "{\"code\":\"" + totp.codeAt(secret, step) + "\"}", token, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.codes", hasSize(10))));
        List<String> recovery = JsonPath.read(codes, "$.data.codes");

        // Password alone now only gives a short-lived challenge, which is not an access token.
        String first = loginBody(email, "Password123!", null);
        assertThat((Boolean) JsonPath.read(first, "$.data.twoFactorRequired")).isTrue();
        assertThat((String) JsonPath.read(first, "$.data.token")).isNull();
        String challenge = JsonPath.read(first, "$.data.challengeToken");
        getAs("/me", challenge).andExpect(status().isUnauthorized());

        postJson("/auth/login/2fa", "{\"challengeToken\":\"" + challenge + "\",\"code\":\"123456\"}", null, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").exists());
        // The code used to turn it on can't be replayed; the next one works.
        postJson("/auth/login/2fa", "{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + totp.codeAt(secret, step) + "\"}", null, null)
                .andExpect(status().isBadRequest());
        String ok = body(postJson("/auth/login/2fa", "{\"challengeToken\":\"" + challenge + "\",\"code\":\"" + totp.codeAt(secret, step + 1) + "\"}", null, null)
                .andExpect(status().isOk()));
        getAs("/me", JsonPath.read(ok, "$.data.token")).andExpect(status().isOk()).andExpect(jsonPath("$.data.twoFactorEnabled").value(true));

        // A recovery code works once, typed in any case and with spaces.
        String typed = recovery.get(0).toLowerCase().replace("-", " ");
        String c2 = JsonPath.read(loginBody(email, "Password123!", null), "$.data.challengeToken");
        postJson("/auth/login/2fa", "{\"challengeToken\":\"" + c2 + "\",\"code\":\"" + typed + "\"}", null, null).andExpect(status().isOk());
        String c3 = JsonPath.read(loginBody(email, "Password123!", null), "$.data.challengeToken");
        postJson("/auth/login/2fa", "{\"challengeToken\":\"" + c3 + "\",\"code\":\"" + typed + "\"}", null, null).andExpect(status().isBadRequest());
        getAs("/me/security", token).andExpect(jsonPath("$.data.recoveryCodesLeft").value(9));

        postJson("/me/2fa/disable", "{\"password\":\"wrong\"}", token, null).andExpect(status().isBadRequest());
        postJson("/me/2fa/disable", "{\"password\":\"Password123!\"}", token, null).andExpect(status().isOk());
        assertThat((String) JsonPath.read(loginBody(email, "Password123!", null), "$.data.token")).isNotBlank();
    }

    @Test
    void adminCanResetTwoStepForALockedOutUser() throws Exception {
        String email = verifiedAccount("Password123!");
        String token = login(email, "Password123!", null);
        String secret = JsonPath.read(body(postJson("/me/2fa/setup", "{}", token, null)), "$.data.secret");
        postJson("/me/2fa/enable", "{\"code\":\"" + totp.codeAt(secret, TotpService.currentStep()) + "\"}", token, null).andExpect(status().isOk());
        Number id = JsonPath.read(body(getAs("/me", token)), "$.data.id");

        String admin = login("root@test.local", "RootPassword1!", null);
        postJson("/users/" + id + "/2fa/reset", "{}", admin, null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.twoFactorEnabled").value(false));
        postJson("/users/" + id + "/2fa/reset", "{}", token, null).andExpect(status().isForbidden());
    }

    /* ── devices ── */

    @Test
    void devicesCanBeListedAndSignedOut() throws Exception {
        String email = verifiedAccount("Password123!");
        String mac = login(email, "Password123!", CHROME_MAC);
        String win = login(email, "Password123!", FIREFOX_WIN);
        String phone = login(email, "Password123!", SAFARI_IPHONE);

        String list = body(getAs("/me/sessions", mac).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(3))));
        assertThat((List<String>) JsonPath.read(list, "$.data[?(@.current==true)].browser")).containsExactly("Chrome");
        assertThat((List<String>) JsonPath.read(list, "$.data[*].os")).containsExactlyInAnyOrder("macOS", "Windows", "iOS");
        String winId = ((List<String>) JsonPath.read(list, "$.data[?(@.browser=='Firefox')].id")).get(0);
        String macId = ((List<String>) JsonPath.read(list, "$.data[?(@.current==true)].id")).get(0);

        mvc.perform(delete("/me/sessions/" + macId).header("Authorization", "Bearer " + mac)).andExpect(status().isBadRequest());
        mvc.perform(delete("/me/sessions/" + winId).header("Authorization", "Bearer " + mac)).andExpect(status().isOk());
        getAs("/me", win).andExpect(status().isUnauthorized());
        getAs("/me", phone).andExpect(status().isOk());

        mvc.perform(delete("/me/sessions").header("Authorization", "Bearer " + mac)).andExpect(jsonPath("$.data.revoked").value(1));
        getAs("/me", phone).andExpect(status().isUnauthorized());
        getAs("/me", mac).andExpect(status().isOk());

        postJson("/auth/logout", "{}", mac, null).andExpect(status().isOk());
        getAs("/me", mac).andExpect(status().isUnauthorized());
    }

    @Test
    void changingPasswordKeepsThisDeviceAndSignsOutOthers() throws Exception {
        String email = verifiedAccount("Password123!");
        String a = login(email, "Password123!", CHROME_MAC);
        String b = login(email, "Password123!", FIREFOX_WIN);
        String fresh = JsonPath.read(body(mvc.perform(put("/me/password").header("Authorization", "Bearer " + a).header("User-Agent", CHROME_MAC)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"Password123!\",\"newPassword\":\"Password456!\"}"))
                .andExpect(status().isOk())), "$.data.token");
        getAs("/me", b).andExpect(status().isUnauthorized());
        getAs("/me", fresh).andExpect(status().isOk());
        getAs("/me/sessions", fresh).andExpect(jsonPath("$.data", hasSize(1)));
    }

    /* ── lock screen, notifications, activity ── */

    @Test
    void lockScreenChecksThePassword() throws Exception {
        String email = verifiedAccount("Password123!");
        String token = login(email, "Password123!", null);
        postJson("/me/verify-password", "{\"password\":\"nope\"}", token, null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.password").exists());
        postJson("/me/verify-password", "{\"password\":\"Password123!\"}", token, null).andExpect(status().isOk());
    }

    @Test
    void notificationsFollowPreferences() throws Exception {
        String email = verifiedAccount("Password123!");
        String token = login(email, "Password123!", CHROME_MAC);
        login(email, "Password123!", FIREFOX_WIN); // a new browser → "New sign-in" alert

        String page = body(getAs("/me/notifications", token).andExpect(status().isOk()));
        assertThat((List<String>) JsonPath.read(page, "$.data.items[*].type")).contains("NEW_SIGN_IN", "WELCOME");
        int unread = JsonPath.read(page, "$.data.unreadCount");
        assertThat(unread).isGreaterThanOrEqualTo(2);
        assertThat(mail.recent()).anyMatch(m -> m.to().equals(email) && m.subject().equals("New sign-in to your account"));

        Number id = JsonPath.read(page, "$.data.items[0].id");
        postJson("/me/notifications/" + id + "/read", "{}", token, null).andExpect(jsonPath("$.data.read").value(true));
        getAs("/me/notifications/unread-count", token).andExpect(jsonPath("$.data.count").value(unread - 1));
        getAs("/me/notifications?unread=true", token).andExpect(jsonPath("$.data.items", hasSize(unread - 1)));
        postJson("/me/notifications/read-all", "{}", token, null).andExpect(status().isOk());
        getAs("/me/notifications/unread-count", token).andExpect(jsonPath("$.data.count").value(0));

        // Someone else's notification can't be touched.
        String other = login(verifiedAccount("Password123!"), "Password123!", null);
        postJson("/me/notifications/" + id + "/read", "{}", other, null).andExpect(status().isNotFound());

        // Turn security alerts off in-app and by email: a new browser no longer creates one.
        mvc.perform(put("/me/notification-settings").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"security\":{\"inApp\":false,\"email\":false},\"account\":{\"inApp\":true,\"email\":false},\"product\":{\"inApp\":true,\"email\":false}}"))
                .andExpect(status().isOk());
        getAs("/me/notification-settings", token).andExpect(jsonPath("$.data.security.inApp").value(false));
        login(email, "Password123!", SAFARI_IPHONE);
        getAs("/me/notifications/unread-count", token).andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void myActivityListsOwnEventsOnly() throws Exception {
        String email = verifiedAccount("Password123!");
        String token = login(email, "Password123!", null);
        postJson("/me/verify-password", "{\"password\":\"Password123!\"}", token, null).andExpect(status().isOk());
        // Sign-in rows are written in the background.
        String body = "";
        for (int i = 0; i < 20; i++) {
            body = body(getAs("/me/activity", token).andExpect(status().isOk()));
            if (((List<?>) JsonPath.read(body, "$.data[?(@.action=='LOGIN')]")).size() > 0) break;
            Thread.sleep(150);
        }
        assertThat((List<String>) JsonPath.read(body, "$.data[*].action")).contains("LOGIN", "REGISTER");
    }
}
