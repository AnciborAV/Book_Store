package ru.aav;

import com.github.springtestdbunit.annotation.DatabaseSetup;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class BookControllerTest extends IntegrationTest {

    @Test
    @DatabaseSetup(value = "/fixtures/initial.xml")
    void testFound() throws Exception {
        mockMvc.perform(get("/books/3")).andExpect(status().is2xxSuccessful());
    }

    @Test
    @DatabaseSetup(value = "/fixtures/initial.xml")
    void testNotFound() throws Exception {
        mockMvc.perform(get("/books/1")).andExpect(status().is4xxClientError());
    }
}