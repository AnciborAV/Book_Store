package ru.aav;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import ru.aav.catalogue.CatalogueOrderService;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:catalogueorder;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class CatalogueOrderTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate db;@Autowired CatalogueOrderService ordering;
    JsonNode catalogue()throws Exception{return json.readTree(mvc.perform(get("/api/catalogue")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    List<CatalogueOrderService.BookKey> keys(JsonNode data){var list=new ArrayList<CatalogueOrderService.BookKey>();data.get("books").forEach(b->list.add(new CatalogueOrderService.BookKey(b.get("kind").asText(),b.get("id").asText())));return list;}
    String body(long version,List<CatalogueOrderService.BookKey> books)throws Exception{return json.writeValueAsString(Map.of("version",version,"books",books));}
    void save(JsonNode data,List<CatalogueOrderService.BookKey> books)throws Exception {
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body(data.get("orderVersion").asLong(),books))).andExpect(status().isNoContent());
    }
    String create(String title)throws Exception {
        mvc.perform(multipart("/api/admin/catalogue/books").param("title",title).param("sectionId","ai").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated());
        return String.valueOf(db.queryForObject("SELECT id FROM books WHERE name=?",Long.class,title));
    }
    String pdf()throws Exception {
        try(var in=getClass().getResourceAsStream("/java-library-sample.pdf")) {
            var result=mvc.perform(multipart("/api/admin/java-library").file(new MockMultipartFile("pdf","book.pdf","application/pdf",in.readAllBytes())).param("title","PDF "+UUID.randomUUID()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated()).andReturn();
            return json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        }
    }
    @Test void mixedBookOrderIsPublishedAndNewCardsAppendWithoutRearrangingIt()throws Exception {
        String file=pdf();var snapshot=catalogue();var requested=keys(snapshot);Collections.reverse(requested);save(snapshot,requested);
        assertThat(keys(catalogue())).isEqualTo(requested);
        assertThat(requested).contains(new CatalogueOrderService.BookKey("library",file));
        String id=create("AAA новая "+UUID.randomUUID());var saved=keys(catalogue());
        assertThat(saved.subList(0,requested.size())).isEqualTo(requested);assertThat(saved.getLast()).isEqualTo(new CatalogueOrderService.BookKey("catalog",id));
        mvc.perform(get("/api/catalogue").with(user("reader"))).andExpect(status().isOk());
        mvc.perform(get("/book-ordering.js")).andExpect(status().isOk());mvc.perform(get("/book-ordering.css")).andExpect(status().isOk());
    }
    @Test void staleDuplicateAndIncompleteLayoutsDoNotOverwriteSavedOrder()throws Exception {
        var old=catalogue();var desired=keys(old);Collections.rotate(desired,1);save(old,desired);var snapshot=catalogue();long version=snapshot.get("orderVersion").asLong();
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body(old.get("orderVersion").asLong(),keys(old)))).andExpect(status().isConflict());
        var duplicate=new ArrayList<>(desired);duplicate.set(0,duplicate.get(1));
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body(version,duplicate))).andExpect(status().isBadRequest());
        var incomplete=new ArrayList<>(desired);incomplete.removeLast();
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body(version,incomplete))).andExpect(status().isConflict());
        assertThat(keys(catalogue())).isEqualTo(desired);assertThat(catalogue().get("orderVersion").asLong()).isEqualTo(version);
    }
    @Test void membershipChangesRequireRefreshingTheShelf()throws Exception {
        var snapshot=catalogue();create("Новая карточка "+UUID.randomUUID());
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(body(snapshot.get("orderVersion").asLong(),keys(snapshot)))).andExpect(status().isConflict());
    }
    @Test void pricingAnExistingPdfKeepsItsShelfPosition()throws Exception {
        String file=pdf();var snapshot=catalogue();var desired=keys(snapshot);var original=new CatalogueOrderService.BookKey("library",file);desired.remove(original);desired.addFirst(original);save(snapshot,desired);
        var book=json.readTree(mvc.perform(get("/api/catalogue/books/library/"+file)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/admin/catalogue/books/library/"+file).param("title",book.get("title").asText()).param("sectionId",book.get("sectionId").asText()).param("version",book.get("version").asText()).param("price","100")
            .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        var first=catalogue().get("books").get(0);assertThat(first.get("kind").asText()).isEqualTo("catalog");assertThat(first.get("pdfId").asText()).isEqualTo(file);
        assertThat(keys(catalogue()).subList(1,desired.size())).isEqualTo(desired.subList(1,desired.size()));
    }
    @Test void onlyAdministratorWithCsrfMaySave()throws Exception {
        var data=catalogue();String body=body(data.get("orderVersion").asLong(),keys(data));
        mvc.perform(put("/api/admin/catalogue/order").with(csrf()).contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/admin/catalogue/order").with(user("reader")).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/catalogue/order").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"version\":0,\"books\":[{\"kind\":\"invalid\",\"id\":\"1\"}]}" )).andExpect(status().isBadRequest());
    }
    @Test void simultaneousAdministratorsCannotLoseEachOthersChanges()throws Exception {
        var data=catalogue();var first=keys(data);Collections.rotate(first,1);var second=keys(data);Collections.reverse(second);
        long version=data.get("orderVersion").asLong();var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{start.await();try{ordering.save(version,first);return 204;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}});
            var b=pool.submit(()->{start.await();try{ordering.save(version,second);return 204;}catch(org.springframework.web.server.ResponseStatusException e){return e.getStatusCode().value();}});
            start.countDown();assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(204,409);
            assertThat(catalogue().get("orderVersion").asLong()).isEqualTo(version+1);
        }finally{pool.shutdownNow();}
    }
}
