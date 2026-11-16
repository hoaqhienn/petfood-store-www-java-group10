package vn.edu.dhktpm19att.group10.petfood.pet;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PetProfileIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    long owner;
    long other;

    @BeforeEach
    void fixture() {
        owner = account("pet-owner@example.test");
        other = account("pet-other@example.test");
    }

    @Test
    void ownerCreatesListsUpdatesAndDeletesSeveralPets() throws Exception {
        String milo = id(send(post("/api/v1/me/pets"), owner, """
                {"name":" Milo ","species":"CAT","birthDate":"2022-05-01","weightGrams":4200,"preferences":"Thích vị cá"}
                """).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("Milo"))
                .andExpect(jsonPath("$.birthDate").value("2022-05-01")).andExpect(jsonPath("$.version").value(0)));
        send(post("/api/v1/me/pets"), owner, "{\"name\":\"Lu\",\"species\":\"DOG\",\"estimatedAgeMonths\":18}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/me/pets").with(as(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Milo")).andExpect(jsonPath("$[1].species").value("DOG"));

        send(patch("/api/v1/me/pets/{id}", milo), owner, "{\"weightGrams\":4500,\"preferences\":null,\"version\":0}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.weightGrams").value(4500))
                .andExpect(jsonPath("$.preferences").value(org.hamcrest.Matchers.nullValue())).andExpect(jsonPath("$.name").value("Milo"))
                .andExpect(jsonPath("$.version").value(1));
        send(patch("/api/v1/me/pets/{id}", milo), owner, "{\"name\":\"Stale\",\"version\":0}")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));

        mvc.perform(delete("/api/v1/me/pets/{id}", milo).with(as(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/me/pets").with(as(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void anotherCustomersPetIsNotFoundAndUntouched() throws Exception {
        String pet = id(send(post("/api/v1/me/pets"), owner, "{\"name\":\"Milo\",\"species\":\"CAT\"}")
                .andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/me/pets/{id}", pet).with(as(other)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PET_NOT_FOUND"));
        send(patch("/api/v1/me/pets/{id}", pet), other, "{\"name\":\"Taken\",\"version\":0}")
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/me/pets/{id}", pet).with(as(other)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/me/pets").with(as(other)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        // ownerId in the body never moves a pet to another account.
        send(post("/api/v1/me/pets"), other, "{\"name\":\"Sneaky\",\"species\":\"CAT\",\"ownerId\":\"" + owner + "\"}")
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT name FROM pet_profile WHERE id = ?", String.class, Long.parseLong(pet)))
                .isEqualTo("Milo");
    }

    @Test
    void invalidProfilesAreRejectedWithoutSaving() throws Exception {
        String tomorrow = LocalDate.now().plusDays(2).toString();
        send(post("/api/v1/me/pets"), owner, "{\"species\":\"CAT\"}").andExpect(status().isUnprocessableEntity());
        send(post("/api/v1/me/pets"), owner, "{\"name\":\"Milo\",\"species\":\"BIRD\"}")
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/v1/me/pets"), owner, "{\"name\":\"Milo\",\"species\":\"CAT\",\"birthDate\":\"" + tomorrow + "\"}")
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/v1/me/pets"), owner, "{\"name\":\"Milo\",\"species\":\"CAT\",\"weightGrams\":2.5}")
                .andExpect(status().isUnprocessableEntity());
        send(post("/api/v1/me/pets"), owner, "{\"name\":\"Milo\",\"species\":\"CAT\",\"estimatedAgeMonths\":-1}")
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pet_profile WHERE owner_id = ?", Integer.class, owner))
                .isZero();
    }

    @Test
    void guestsAndLockedAccountsCannotManagePets() throws Exception {
        mvc.perform(get("/api/v1/me/pets")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/pets").with(as(owner)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Milo\",\"species\":\"CAT\"}"))
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE user_account SET status = 'LOCKED' WHERE id = ?", owner);
        mvc.perform(get("/api/v1/me/pets").with(as(owner)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void deletingPetsNeverTouchesOrdersAndDeletingAccountRemovesPets() throws Exception {
        String pet = id(send(post("/api/v1/me/pets"), owner, "{\"name\":\"Milo\",\"species\":\"CAT\"}")
                .andExpect(status().isCreated()));
        UUID order = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customer_order (id, order_code, customer_id, recipient_name, phone, address_text,
                                            subtotal, shipping_fee, grand_total)
                VALUES (?, ?, ?, 'Receiver', '0900000000', 'Local QA', 0, 0, 0)
                """, order, "PF-" + order, owner);
        mvc.perform(delete("/api/v1/me/pets/{id}", pet).with(as(owner)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE id = ?", Integer.class, order))
                .isEqualTo(1);

        send(post("/api/v1/me/pets"), other, "{\"name\":\"Lu\",\"species\":\"DOG\"}").andExpect(status().isCreated());
        jdbc.update("DELETE FROM user_account WHERE id = ?", other);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pet_profile WHERE owner_id = ?", Integer.class, other))
                .isZero();
    }

    private ResultActions send(MockHttpServletRequestBuilder request, long actor, String body) throws Exception {
        return mvc.perform(request.with(as(actor)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body.strip()));
    }

    private String id(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private static RequestPostProcessor as(long actor) {
        return user(Long.toString(actor)).roles("CUSTOMER");
    }

    private long account(String email) {
        return jdbc.queryForObject("""
                INSERT INTO user_account (email_normalized, display_name, password_hash, role, status)
                VALUES (?, 'Fixture', 'unused', 'CUSTOMER', 'ACTIVE') RETURNING id
                """, Long.class, email);
    }
}
