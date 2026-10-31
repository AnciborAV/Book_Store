package ru.aav;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import ru.aav.auth.*;
import ru.aav.commerce.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:cataloguemgmt;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class CatalogueManagementTest {
    @Autowired MockMvc mvc;@Autowired JdbcTemplate db;@Autowired ObjectMapper json;
    @Autowired AccountService accounts;@Autowired OrderService orders;
    String title(){return "Книга "+UUID.randomUUID();}
    JsonNode entry(String kind,String id)throws Exception{return json.readTree(mvc.perform(get("/api/catalogue/books/"+kind+"/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    JsonNode section(String name)throws Exception{return json.readTree(mvc.perform(post("/api/admin/catalogue/sections").with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json").content(json.writeValueAsString(Map.of("name",name)))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));}
    String create(String name,String section,MockMultipartFile... files)throws Exception {
        var request=multipart("/api/admin/catalogue/books"); request.param("title",name).param("author","Автор").param("sectionId",section).param("description","Описание");
        for(var file:files)request.file(file);
        mvc.perform(request.with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated());
        return String.valueOf(db.queryForObject("SELECT id FROM books WHERE name=?",Long.class,name));
    }
    MockMultipartFile cover()throws IOException {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(120,200,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("cover","cover.png","image/png",out.toByteArray());}
    MockMultipartFile pdf()throws IOException {try(var in=getClass().getResourceAsStream("/java-library-sample.pdf")){return new MockMultipartFile("pdf","book.pdf","application/pdf",in.readAllBytes());}}
    MockMultipartHttpServletRequestBuilder edit(String kind,String id,JsonNode record,String section) {
        var request=multipart("/api/admin/catalogue/books/"+kind+"/"+id);request.param("title",record.get("title").asText()).param("author",record.get("author").asText())
            .param("sectionId",section).param("description",record.get("description").asText()).param("version",record.get("version").asText()).param("pdfVersion",record.get("pdfVersion").asText());return request;
    }
    @Test void digitalSectionIsManagedAndAcceptsCoverThenPdf()throws Exception {
        var section=db.queryForMap("SELECT * FROM store_sections WHERE id='digital-technologies'");
        assertThat(section.get("name")).isEqualTo("Современные цифровые технологии");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM store_sections WHERE name='Современные цифровые технологии'",Integer.class)).isEqualTo(1);
        String id=create(title(),"digital-technologies",cover());var initial=entry("catalog",id);
        assertThat(initial.get("sectionId").asText()).isEqualTo("digital-technologies");
        assertThat(initial.get("pdfId").isNull()).isTrue();
        mvc.perform(edit("catalog",id,initial,"digital-technologies").file(pdf()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        assertThat(entry("catalog",id).get("pdfId").isNull()).isFalse();
    }
    @Test void coverFirstThenPdfKeepsCardCoverAndCollections()throws Exception {
        String id=create(title(),"java",cover());var initial=entry("catalog",id);assertThat(initial.get("pdfId").isNull()).isTrue();
        byte[] image=mvc.perform(get("/api/catalogue/covers/"+id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var editorial=json.readTree(mvc.perform(get("/api/editorial/books/catalog/"+id)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(put("/api/admin/editorial/books/catalog/"+id).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("version",editorial.get("version").asLong(),"description","Описание","lists",List.of("Мои любимые книги"),"comment","Рекомендую")))).andExpect(status().isOk());
        mvc.perform(edit("catalog",id,initial,"java").file(pdf()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        var saved=entry("catalog",id);assertThat(saved.get("id").asText()).isEqualTo(id);assertThat(saved.get("pdfId").isNull()).isFalse();
        mvc.perform(get("/api/catalogue/covers/"+id)).andExpect(content().bytes(image));
        String pdf=saved.get("pdfId").asText();mvc.perform(get("/api/java-library/"+pdf+"/download")).andExpect(content().bytes(pdf().getBytes()));
        mvc.perform(get("/api/java-library/"+pdf+"/content").param("version",saved.get("pdfVersion").asText())).andExpect(status().isOk());
        mvc.perform(get("/api/editorial/books/catalog/"+id)).andExpect(jsonPath("$.lists[0]").value("Мои любимые книги")).andExpect(jsonPath("$.pdfId").value(pdf));
        var catalogue=json.readTree(mvc.perform(get("/api/catalogue")).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(catalogue.get("books").findValuesAsText("id")).doesNotContain(pdf); // No duplicate PDF card.
    }
    @Test void renameMoveAndDeleteSection()throws Exception {
        var first=section("Раздел "+UUID.randomUUID());var second=section("Другой "+UUID.randomUUID());String id=create(title(),first.get("id").asText(),cover(),pdf());
        String name="Новое название "+UUID.randomUUID();
        mvc.perform(put("/api/admin/catalogue/sections/"+first.get("id").asText()).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("name",name,"position",1,"version",0)))).andExpect(status().isOk());
        var book=entry("catalog",id);assertThat(book.get("sectionName").asText()).isEqualTo(name);
        mvc.perform(delete("/api/admin/catalogue/sections/"+first.get("id").asText()).param("version","1").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        String pdfId=book.get("pdfId").asText();mvc.perform(edit("catalog",id,book,second.get("id").asText()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        assertThat(entry("catalog",id).get("pdfId").asText()).isEqualTo(pdfId);
        mvc.perform(delete("/api/admin/catalogue/sections/"+first.get("id").asText()).param("version","1").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void administratorCanMoveLegacyPdfToNewSection()throws Exception {
        var created=mvc.perform(multipart("/api/admin/java-library").file(pdf()).param("title",title()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated()).andReturn();
        String id=json.readTree(created.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("id").asText();var section=section("Философия ИИ "+UUID.randomUUID());
        var book=entry("library",id);byte[] cover=mvc.perform(get("/api/java-library/"+id+"/cover")).andReturn().getResponse().getContentAsByteArray();
        mvc.perform(edit("library",id,book,section.get("id").asText()).file(pdf()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/java-library/"+id+"/cover")).andExpect(content().bytes(cover));
        assertThat(entry("library",id).get("sectionId").asText()).isEqualTo(section.get("id").asText());
    }
    @Test void removePdfKeepsChosenCoverAndCard()throws Exception {
        String id=create(title(),"java",cover(),pdf());var book=entry("catalog",id);String file=book.get("pdfId").asText();
        mvc.perform(edit("catalog",id,book,"java").param("removePdf","true").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        assertThat(entry("catalog",id).get("pdfId").isNull()).isTrue();mvc.perform(get("/api/catalogue/covers/"+id)).andExpect(status().isOk());
        mvc.perform(get("/api/java-library/"+file+"/download")).andExpect(status().isNotFound());
    }
    @Test void deletionHidesBookButPreservesOrderHistoryAndCancellation()throws Exception {
        String name=title(),id=create(name,"java",cover(),pdf());long key=Long.parseLong(id);
        String email=UUID.randomUUID()+"@example.org";accounts.register(new Registration(email,"Читатель","test-password"));
        db.update("UPDATE books SET price=10,stock=3 WHERE id=?",key);
        mvc.perform(put("/api/selection/cart/"+id).with(user(email)).with(csrf()).contentType("application/json").content("{\"quantity\":1}")).andExpect(status().isNoContent());
        var order=orders.checkout(email,UUID.randomUUID());var book=entry("catalog",id);
        mvc.perform(delete("/api/admin/catalogue/books/catalog/"+id).param("version",book.get("version").asText()).param("pdfVersion",book.get("pdfVersion").asText()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/books/"+id)).andExpect(status().isNotFound());mvc.perform(get("/api/catalogue/books/catalog/"+id)).andExpect(status().isNotFound());
        assertThat(orders.detail(email,order.order().id()).items().getFirst().name()).isEqualTo(name);
        String replacement=create(name,"ai",cover());assertThat(replacement).isNotEqualTo(id);
        orders.transition(email,order.order().id(),OrderStatus.CANCELLED,false);
        assertThat(db.queryForObject("SELECT reserved FROM books WHERE id=?",Integer.class,key)).isZero();
        mvc.perform(get("/api/java-library/"+book.get("pdfId").asText()+"/download")).andExpect(status().isNotFound());
    }
    @Test void staleVersionsCannotOverwriteOrDelete()throws Exception {
        String id=create(title(),"java",cover());var old=entry("catalog",id);
        mvc.perform(edit("catalog",id,old,"ai").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(edit("catalog",id,old,"java").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/catalogue/books/catalog/"+id).param("version","0").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        assertThat(entry("catalog",id).get("sectionId").asText()).isEqualTo("ai");
    }
    @Test void priceCanBeCreatedChangedAndRemovedAndOrdersKeepTheirPrice()throws Exception {
        String name=title();
        mvc.perform(multipart("/api/admin/catalogue/books").param("title",name).param("sectionId","java").param("price","1250.50")
            .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated());
        String id=String.valueOf(db.queryForObject("SELECT id FROM books WHERE name=?",Long.class,name));
        assertThat(entry("catalog",id).get("price").decimalValue()).isEqualByComparingTo("1250.50");
        db.update("UPDATE books SET stock=5 WHERE id=?",Long.parseLong(id));
        String email=UUID.randomUUID()+"@example.org";accounts.register(new Registration(email,"Покупатель","test-password"));
        mvc.perform(put("/api/selection/cart/"+id).with(user(email)).with(csrf()).contentType("application/json").content("{\"quantity\":1}")).andExpect(status().isNoContent());
        var first=orders.checkout(email,UUID.randomUUID());
        mvc.perform(edit("catalog",id,entry("catalog",id),"java").param("price","1499.99").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        assertThat(entry("catalog",id).get("price").decimalValue()).isEqualByComparingTo("1499.99");
        assertThat(orders.detail(email,first.order().id()).items().getFirst().unitPrice()).isEqualByComparingTo("1250.50");
        db.update("UPDATE store_users SET role='ADMIN' WHERE email=?",email);
        mvc.perform(put("/api/selection/cart/"+id).with(user(email).roles("ADMIN")).with(csrf()).contentType("application/json").content("{\"quantity\":1}")).andExpect(status().isNoContent());
        assertThat(orders.checkout(email,UUID.randomUUID()).order().total()).isEqualByComparingTo("1499.99");
        mvc.perform(edit("catalog",id,entry("catalog",id),"java").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        assertThat(entry("catalog",id).get("price").isNull()).isTrue();
        mvc.perform(put("/api/selection/cart/"+id).with(user(email)).with(csrf()).contentType("application/json").content("{\"quantity\":1}")).andExpect(status().isNoContent());
        assertThatThrownBy(()->orders.checkout(email,UUID.randomUUID())).hasMessageContaining("Не указана цена");
    }
    @Test void legacyPdfCanBePricedWithoutLosingFileCoverCollectionsOrFavorites()throws Exception {
        var result=mvc.perform(multipart("/api/admin/java-library").file(pdf()).file(cover()).param("title",title()).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isCreated()).andReturn();
        String pdfId=json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        byte[] image=mvc.perform(get("/api/java-library/"+pdfId+"/cover")).andReturn().getResponse().getContentAsByteArray();
        String email=UUID.randomUUID()+"@example.org";accounts.register(new Registration(email,"Читатель","test-password"));
        mvc.perform(put("/api/selection/library-favorites/"+pdfId).with(user(email)).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(put("/api/admin/editorial/books/library/"+pdfId).with(user("admin").roles("ADMIN")).with(csrf()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("version",-1,"description","Описание","lists",List.of("Любимые PDF"),"comment","Советую")))).andExpect(status().isOk());
        var initial=entry("library",pdfId);
        mvc.perform(edit("library",pdfId,initial,"java").param("price","700.01").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        String id=String.valueOf(db.queryForObject("SELECT book_id FROM catalog_pdf WHERE library_id=?",Long.class,pdfId));
        var saved=entry("catalog",id);assertThat(saved.get("price").decimalValue()).isEqualByComparingTo("700.01");assertThat(saved.get("pdfId").asText()).isEqualTo(pdfId);
        mvc.perform(get("/api/catalogue/covers/"+id)).andExpect(content().bytes(image));
        mvc.perform(get("/api/java-library/"+pdfId+"/download")).andExpect(content().bytes(pdf().getBytes()));
        mvc.perform(get("/api/editorial/books/catalog/"+id)).andExpect(jsonPath("$.lists[0]").value("Любимые PDF")).andExpect(jsonPath("$.comment").value("Советую"));
        mvc.perform(get("/api/selection").with(user(email))).andExpect(jsonPath("$.favorites[0]").value(Long.parseLong(id)));
        mvc.perform(edit("library",pdfId,initial,"java").param("price","800").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNotFound());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM catalog_pdf WHERE library_id=?",Integer.class,pdfId)).isEqualTo(1);
    }
    @Test void invalidPricesAndUnauthorizedEditsAreRejected()throws Exception {
        String id=create(title(),"java",cover());var book=entry("catalog",id);
        for(String price:List.of("0","-1","10.001","100000000","NaN","abc")) {
            mvc.perform(edit("catalog",id,book,"java").param("price",price).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
            String name=title();mvc.perform(multipart("/api/admin/catalogue/books").param("title",name).param("sectionId","java").param("price",price).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
            assertThat(db.queryForObject("SELECT COUNT(*) FROM books WHERE name=?",Integer.class,name)).isZero();
        }
        mvc.perform(edit("catalog",id,book,"java").param("price","100").with(user("reader")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(edit("catalog",id,book,"java").param("price","100").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        assertThat(entry("catalog",id).get("price").isNull()).isTrue();
    }
    @Test void accessControlsAndInvalidUploads()throws Exception {
        mvc.perform(post("/api/admin/catalogue/sections").with(csrf()).contentType("application/json").content("{\"name\":\"Blocked\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/catalogue/sections").with(user("reader").roles("CUSTOMER")).with(csrf()).contentType("application/json").content("{\"name\":\"Blocked\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/catalogue/sections").with(user("admin").roles("ADMIN")).contentType("application/json").content("{\"name\":\"Blocked\"}")).andExpect(status().isForbidden());
        String name=title();mvc.perform(multipart("/api/admin/catalogue/books").file(new MockMultipartFile("pdf","fake.pdf","application/pdf","invalid".getBytes())).param("title",name).param("sectionId","java").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT COUNT(*) FROM books WHERE name=?",Integer.class,name)).isZero();
    }
}
