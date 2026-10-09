package com.atelier.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * End-to-end tests through the HTTP layer against a real database.
 * Run with: ./mvnw test   (needs PostgreSQL; see application-test.properties)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTests {

    @Autowired
    MockMvc mvc;

    String admin;

    @BeforeEach
    void signInAsAdmin() throws Exception {
        admin = login("root@test.local", "RootPassword1!");
    }

    /* ── helpers ── */

    String login(String email, String password) throws Exception {
        MvcResult r = mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn();
        return JsonPath.read(r.getResponse().getContentAsString(), "$.data.token");
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    static String unique() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    long roleId(String name) throws Exception {
        String body = mvc.perform(get("/roles").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(body, "$.data[?(@.roleName=='" + name + "')].id");
        return ids.get(0).longValue();
    }

    long createUser(String email, String password, String role) throws Exception {
        String body = mvc.perform(post("/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"roleIds\":[" + roleId(role) + "]}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    /* ── authentication ── */

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.local\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"root@test.local\",\"password\":\"wrong-" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }

    @Test
    void protectedEndpointsNeedAToken() throws Exception {
        mvc.perform(get("/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/users").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    void repeatedFailuresLockTheAccountTemporarily() throws Exception {
        String email = unique();
        createUser(email, "Password123!", "USER");
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + email + "\",\"password\":\"bad\"}")).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void logoutRevokesTheToken() throws Exception {
        String token = login("root@test.local", "RootPassword1!");
        mvc.perform(post("/auth/logout").header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(get("/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
    }

    /* ── permissions ── */

    @Test
    void permissionsAreEnforced() throws Exception {
        String email = unique();
        createUser(email, "Password123!", "USER");
        String user = login(email, "Password123!");
        mvc.perform(get("/dashboard/summary").header("Authorization", bearer(user))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentUsers").doesNotExist());
        mvc.perform(get("/users").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mvc.perform(get("/audit-logs").header("Authorization", bearer(user))).andExpect(status().isForbidden());
    }

    @Test
    void systemRoleIsProtectedAndHiddenFromAdmins() throws Exception {
        long superAdmin = roleId("SUPER_ADMIN");
        mvc.perform(put("/roles/" + superAdmin + "/permissions").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isForbidden());

        String email = unique();
        createUser(email, "Password123!", "ADMIN");
        String other = login(email, "Password123!");
        mvc.perform(get("/roles").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].roleName", not(hasItem("SUPER_ADMIN"))));
    }

    /* ── users ── */

    @Test
    void userLifecycle() throws Exception {
        String email = unique();
        long id = createUser(email.toUpperCase(), "Password123!", "USER");

        mvc.perform(get("/users/" + id).header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.email").value(email)); // stored lower-case

        mvc.perform(post("/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Dup\",\"email\":\"" + email + "\",\"password\":\"Password123!\"}"))
                .andExpect(status().isConflict());

        String token = login(email, "Password123!");
        mvc.perform(patch("/users/" + id + "/status").header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mvc.perform(get("/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());

        mvc.perform(delete("/users/" + id).header("Authorization", bearer(admin))).andExpect(status().isOk());
        mvc.perform(get("/users/" + id).header("Authorization", bearer(admin))).andExpect(status().isNotFound());
    }

    @Test
    void changingPasswordSignsOutOtherSessions() throws Exception {
        String email = unique();
        createUser(email, "Password123!", "USER");
        String old = login(email, "Password123!");
        Thread.sleep(1100); // tokens carry whole-second timestamps
        String body = mvc.perform(put("/me/password").header("Authorization", bearer(old)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Password123!\",\"newPassword\":\"Another123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String fresh = JsonPath.read(body, "$.data.token");
        assertThat(fresh).isNotBlank();
        mvc.perform(get("/me").header("Authorization", bearer(old))).andExpect(status().isUnauthorized());
        mvc.perform(get("/me").header("Authorization", bearer(fresh))).andExpect(status().isOk());
    }

    @Test
    void validationErrorsNameTheField() throws Exception {
        mvc.perform(post("/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").exists())
                .andExpect(jsonPath("$.data.password").exists());
    }

    /* ── audit ── */

    @Test
    void superAdminCanReadTheAuditLog() throws Exception {
        mvc.perform(get("/audit-logs").param("category", "AUTH").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].category").value("AUTH"));
    }
}
