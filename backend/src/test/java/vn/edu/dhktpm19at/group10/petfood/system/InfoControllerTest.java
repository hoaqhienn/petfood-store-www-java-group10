package vn.edu.dhktpm19at.group10.petfood.system;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.dhktpm19at.group10.petfood.config.SecurityConfig;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InfoController.class)
@Import(SecurityConfig.class)
class InfoControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void guestCanReadProjectInfo() throws Exception {
        mvc.perform(get("/api/v1/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("PetFood"))
                .andExpect(jsonPath("$.stage").value("scaffold"));
    }

    @Test
    void unfinishedAdminApiIsNotPublic() throws Exception {
        mvc.perform(get("/api/v1/admin/users")).andExpect(status().isForbidden());
    }

    @Test
    void mutationWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/v1/info")).andExpect(status().isForbidden());
    }
}

