package ru.aav;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

abstract class JavaLibraryContract {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    byte[] original;
    @BeforeEach void reset() throws Exception {
        jdbc.update("DELETE FROM java_library");
        try(var in=getClass().getResourceAsStream("/java-library-sample.pdf")) { original=in.readAllBytes(); }
    }
    MockMultipartFile pdf() { return new MockMultipartFile("pdf","java.pdf","application/pdf",original); }
    String create(String name) throws Exception {
        var response=mvc.perform(multipart("/api/admin/java-library").file(pdf()).param("title",name)
            .param("author","Автор").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.pageCount").value(2)).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("id").asText();
    }
    @Test void publicDownloadPreservesOriginalAndAdminCanEditDelete() throws Exception {
        String id=create("Java с нуля");
        mvc.perform(get("/api/java-library")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Java с нуля"));
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(status().isOk())
            .andExpect(content().bytes(original)).andExpect(header().string("Content-Type","application/pdf"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("attachment;")));
        var cover=mvc.perform(get("/api/java-library/"+id+"/cover")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        var img=ImageIO.read(new ByteArrayInputStream(cover));
        assertThat((double)img.getWidth()/img.getHeight()).isCloseTo(2d/3,org.assertj.core.data.Offset.offset(.01));
        mvc.perform(multipart("/api/admin/java-library/"+id).param("version","0").param("title","Новое название")
            .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(content().bytes(original));
        mvc.perform(get("/api/java-library/"+id+"/cover")).andExpect(content().bytes(cover));
        mvc.perform(delete("/api/admin/java-library/"+id).param("version","0").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(delete("/api/admin/java-library/"+id).param("version","1").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM java_library_files",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM java_library_pdf_chunks",Integer.class)).isZero();
    }
    @Test void mutationsRequireAdminAndCsrf() throws Exception {
        mvc.perform(multipart("/api/admin/java-library").file(pdf()).param("title","x").with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/admin/java-library").file(pdf()).param("title","x").with(user("buyer").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/admin/java-library").file(pdf()).param("title","x").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        String id=create("Protected");
        mvc.perform(multipart("/api/admin/java-library/"+id).param("version","0").param("title","Changed").with(user("buyer")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/java-library/"+id).param("version","0").with(user("buyer")).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void invalidFileDoesNotPublishAndCoverKeepsAspectRatio() throws Exception {
        mvc.perform(multipart("/api/admin/java-library").file(new MockMultipartFile("pdf","fake.pdf","application/pdf","not a PDF".getBytes()))
            .param("title","Bad").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM java_library",Integer.class)).isZero();
        String id=create("Java");
        var image=new BufferedImage(300,100,BufferedImage.TYPE_INT_RGB); var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
        mvc.perform(multipart("/api/admin/java-library/"+id).file(new MockMultipartFile("cover","cover.png","image/png",out.toByteArray()))
            .param("version","0").param("title","Java").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        var bytes=mvc.perform(get("/api/java-library/"+id+"/cover")).andReturn().getResponse().getContentAsByteArray();
        var result=ImageIO.read(new ByteArrayInputStream(bytes));assertThat(result.getWidth()).isEqualTo(300);assertThat(result.getHeight()).isEqualTo(100);
        mvc.perform(multipart("/api/admin/java-library/"+id).file(new MockMultipartFile("pdf","broken.pdf","application/pdf","%PDF-broken".getBytes()))
            .param("version","1").param("title","Damaged").with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(content().bytes(original));
    }
    @Test void libraryPaginatesWithoutBookCountLimitAndSearchesLiterally() throws Exception {
        for(int i=0;i<13;i++) create("Java " + i);
        mvc.perform(get("/api/java-library")).andExpect(jsonPath("$.total").value(13)).andExpect(jsonPath("$.items.length()").value(12));
        mvc.perform(get("/api/java-library?page=1")).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/java-library").param("q","%")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/java-library").param("q","Java 12")).andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/api/java-library?page=-1")).andExpect(status().isBadRequest());
    }
}
