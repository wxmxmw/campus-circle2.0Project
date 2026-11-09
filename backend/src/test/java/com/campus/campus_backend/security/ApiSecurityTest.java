package com.campus.campus_backend.security;

import com.campus.campus_backend.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;

    @Test
    void anonymousRequestsCannotCallWriteOrAiEndpoints() throws Exception {
        mvc.perform(post("/api/ai/post-assist").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/me/points").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    @Test
    void defaultConfigurationDoesNotCreateAnAdministrator() {
        assertFalse(users.findByUsername("admin").isPresent());
    }
}
