package ru.aav;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:about;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class AboutSectionTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @BeforeEach void reset() {
        db.update("UPDATE site_about SET title='О магазине',lead='Введение',body=?,signature='Автор',link_label='В каталог',photo_alt='Автор',version=0 WHERE id=1","Первый абзац\n\nВторой абзац");
        db.update("UPDATE site_about_images SET enabled=TRUE,data=NULL");
    }
    Map<String,Object> input(long version) {
        var data=new java.util.HashMap<String,Object>();
        data.put("title","Новый заголовок");data.put("lead","Вступление");data.put("body","Текст <script>alert(1)</script>\n\nДругой абзац");
        data.put("signature","Подпись");data.put("linkLabel","Читать книги");data.put("photoAlt","Фотография");data.put("version",version);
        data.put("removePortrait",false);data.put("removeDevelopment",false);data.put("removeLibrary",false);return data;
    }
    MockMultipartHttpServletRequestBuilder save(Map<String,Object> content) throws Exception {
        return multipart("/api/admin/about").file(new MockMultipartFile("content","content.json","application/json",json.writeValueAsBytes(content)));
    }
    MockMultipartFile image(String slot,int w,int h,String format) throws IOException {
        var img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        var g=img.createGraphics();g.setColor(java.awt.Color.ORANGE);g.fillRect(0,0,w,h);g.dispose();
        var out=new ByteArrayOutputStream();ImageIO.write(img,format,out);
        return new MockMultipartFile(slot,"image."+format,"image/"+format,out.toByteArray());
    }
    @Test void publicReadAndDefaultImages() throws Exception {
        mvc.perform(get("/api/about")).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("О магазине"))
            .andExpect(jsonPath("$.portrait").value(true)).andExpect(header().string("Cache-Control","no-store"));
        for(String slot:new String[]{"portrait","development","library"})
            mvc.perform(get("/api/about/images/"+slot)).andExpect(status().isOk())
                .andExpect(content().contentType("image/jpeg")).andExpect(header().exists("ETag"));
        mvc.perform(get("/api/about/images/missing")).andExpect(status().isNotFound());
        for(String path:new String[]{"/about.js","/about.css"})mvc.perform(get(path)).andExpect(status().isOk());
        String html=mvc.perform(get("/")).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("Большую часть своей профессиональной жизни", "src=\"/covers/author-andrey.jpeg\"");
    }
    @Test void onlyAdminCanReadEditorAndSaveWithCsrf() throws Exception {
        mvc.perform(get("/api/admin/about")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/about").with(user("reader").roles("CUSTOMER"))).andExpect(status().isForbidden());
        mvc.perform(save(input(0)).with(csrf())).andExpect(status().isUnauthorized());
        mvc.perform(save(input(0)).with(user("reader").roles("CUSTOMER")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(save(input(0)).with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        assertThat(db.queryForObject("SELECT version FROM site_about WHERE id=1",Long.class)).isZero();
    }
    @Test void administratorSavesEveryTextField() throws Exception {
        var change=input(0);
        mvc.perform(save(change).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk())
            .andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.body").value(change.get("body")));
        mvc.perform(get("/api/about")).andExpect(jsonPath("$.signature").value("Подпись"))
            .andExpect(jsonPath("$.linkLabel").value("Читать книги")).andExpect(jsonPath("$.photoAlt").value("Фотография"));
        assertThat(db.queryForObject("SELECT body FROM site_about WHERE id=1",String.class)).isEqualTo(change.get("body"));
    }
    @Test void allTextAndEveryImageCanBeRemoved() throws Exception {
        var change=input(0);
        for(String name:new String[]{"title","lead","body","signature","linkLabel","photoAlt"})change.put(name,"");
        for(String name:new String[]{"removePortrait","removeDevelopment","removeLibrary"})change.put(name,true);
        mvc.perform(save(change).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/about")).andExpect(jsonPath("$.title").value(""))
            .andExpect(jsonPath("$.body").value("")).andExpect(jsonPath("$.portrait").value(false))
            .andExpect(jsonPath("$.development").value(false)).andExpect(jsonPath("$.library").value(false));
        for(String slot:new String[]{"portrait","development","library"})mvc.perform(get("/api/about/images/"+slot)).andExpect(status().isNotFound());
    }
    @Test void replacePortraitPreservesRatioAndDoesNotChangeBackgrounds() throws Exception {
        mvc.perform(save(input(0)).file(image("portrait",300,100,"png")).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        byte[] bytes=mvc.perform(get("/api/about/images/portrait")).andExpect(content().contentType("image/png")).andReturn().getResponse().getContentAsByteArray();
        var decoded=ImageIO.read(new ByteArrayInputStream(bytes));assertThat(decoded.getWidth()).isEqualTo(300);assertThat(decoded.getHeight()).isEqualTo(100);
        mvc.perform(get("/api/about/images/development")).andExpect(content().contentType("image/jpeg"));
        var change=input(1);change.put("removePortrait",true);
        mvc.perform(save(change).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        mvc.perform(save(input(2)).file(image("portrait",60,120,"png")).file(image("development",120,60,"png"))
            .file(image("library",120,60,"png")).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        for(String slot:new String[]{"portrait","development","library"})mvc.perform(get("/api/about/images/"+slot)).andExpect(content().contentType("image/png"));
    }
    @Test void largePhotosAreResizedWithoutCropping() throws Exception {
        mvc.perform(save(input(0)).file(image("portrait",2400,1200,"png")).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk());
        var bytes=mvc.perform(get("/api/about/images/portrait")).andReturn().getResponse().getContentAsByteArray();
        var decoded=ImageIO.read(new ByteArrayInputStream(bytes));assertThat(decoded.getWidth()).isEqualTo(1600);assertThat(decoded.getHeight()).isEqualTo(800);
    }
    @Test void invalidImagesAndConflictingActionsDoNotSaveText() throws Exception {
        for(var image:new MockMultipartFile[]{new MockMultipartFile("portrait","bad.png","image/png","not an image".getBytes()),
            new MockMultipartFile("portrait","bad.svg","image/svg+xml","<svg/>".getBytes()),
            new MockMultipartFile("portrait","large.png","image/png",new byte[5*1024*1024+1]), image("portrait",6001,1,"png")})
            mvc.perform(save(input(0)).file(image).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        var change=input(0);change.put("removePortrait",true);
        mvc.perform(save(change).file(image("portrait",60,120,"png")).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("SELECT version FROM site_about WHERE id=1",Long.class)).isZero();
    }
    @Test void staleVersionDoesNotOverwriteTextOrImages() throws Exception {
        mvc.perform(save(input(0)).file(image("portrait",80,40,"png")).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        var change=input(0);change.put("removePortrait",true);change.put("title","Stale");
        mvc.perform(save(change).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isConflict());
        mvc.perform(get("/api/about")).andExpect(jsonPath("$.title").value("Новый заголовок")).andExpect(jsonPath("$.portrait").value(true));
    }
    @Test void oversizedTextIsRejected() throws Exception {
        var change=input(0);change.put("body","x".repeat(20001));
        mvc.perform(save(change).with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isBadRequest());
    }
    @Test void phonePhotoExifRotationIsPreserved() throws Exception {
        byte[] jpeg=image("portrait",80,40,"jpeg").getBytes();
        // APP1: Exif, little-endian TIFF with one orientation=6 field.
        byte[] exif={ (byte)255,(byte)225,0,34,'E','x','i','f',0,0,'I','I',42,0,8,0,0,0,1,0,18,1,3,0,1,0,0,0,6,0,0,0,0,0,0,0 };
        var bytes=new ByteArrayOutputStream();bytes.write(jpeg,0,2);bytes.write(exif);bytes.write(jpeg,2,jpeg.length-2);
        mvc.perform(save(input(0)).file(new MockMultipartFile("portrait","phone.jpg","image/jpeg",bytes.toByteArray()))
            .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isOk());
        var result=mvc.perform(get("/api/about/images/portrait")).andReturn().getResponse().getContentAsByteArray();
        var image=ImageIO.read(new ByteArrayInputStream(result));assertThat(image.getWidth()).isEqualTo(40);assertThat(image.getHeight()).isEqualTo(80);
    }
}
