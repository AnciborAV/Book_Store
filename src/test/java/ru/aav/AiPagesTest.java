package ru.aav;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:aipages;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class AiPagesTest {
    @Autowired MockMvc mvc;
    @Test void allAiDocumentsArePublicAndCanOnlyBeEmbeddedByThisSite()throws Exception {
        for(String path:new String[]{"/ai-atlas.html","/ai-architects.html","/ai-history.html","/ai-models.html","/ai-russia.html"})
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("X-Frame-Options","SAMEORIGIN"));
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(header().string("X-Frame-Options","DENY"));
    }
    @Test void readingAtlasPagesAndAssetsArePublicWithoutFramePermission()throws Exception {
        for(String path:new String[]{"/digital-atlas.html","/digital-atlas.css","/digital-atlas.js","/digital-atlas-core.js","/digital-atlas-data.js","/covers/digital-atlas/constellation.svg","/programming-atlas.html","/programming-atlas.js","/programming-atlas-core.js","/programming-atlas-data.js","/programming-atlas.css","/reading-atlas.html","/reading-atlas.js","/reading-atlas-data.js","/reading-atlas.css","/atlas-navigation.js","/atlas-navigation.css"})
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("X-Frame-Options","DENY"));
    }
    @Test void stylesScriptsAndLocalPhotographsArePublic()throws Exception {
        for(String path:new String[]{"/ai-navigation.js","/ai-story.js","/ai-story.css","/ai-models.js","/ai-models.css","/ai-russia.css","/ai-russia.js","/covers/ai-people/turing.jpg","/covers/ai-people/eniac.jpg","/covers/ai-people/network.svg"})
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(header().string("X-Frame-Options","DENY"));
    }
}
