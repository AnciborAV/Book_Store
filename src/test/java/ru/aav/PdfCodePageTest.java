package ru.aav;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:pdfcodepage;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class PdfCodePageTest {
    @Autowired MockMvc mvc;

    @Test void sourceExplanationsAreAvailableWithoutLogin() throws Exception {
        for (String path : new String[]{"/pdf-reading-code.html", "/pdf-download-code.html", "/remember-login-code.html", "/pdf-reading-code.css", "/feedback-code.js"}) {
            mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"));
        }
        mvc.perform(get("/covers/pdf-reading-code.webp")).andExpect(status().isOk())
            .andExpect(content().contentType("image/webp"));
        mvc.perform(get("/covers/pdf-download-code.webp")).andExpect(status().isOk())
            .andExpect(content().contentType("image/webp"));
        mvc.perform(get("/covers/remember-login-code.webp")).andExpect(status().isOk())
            .andExpect(content().contentType("image/webp"));
    }

    @Test void sourcePageDoesNotGrantAccessToPrivateActions() throws Exception {
        mvc.perform(get("/api/admin/site-feedback")).andExpect(status().isUnauthorized());
    }
}
