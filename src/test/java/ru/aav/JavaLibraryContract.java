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
    @Autowired ru.aav.library.JavaLibraryService library;
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
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"civil-law","ai"})
    void sectionUploadsStayInTheirSectionAndCanBeEdited(String section) throws Exception {
        create("Java existing");
        var result=mvc.perform(multipart("/api/admin/java-library").file(pdf())
            .param("title","Гражданское право").param("author","Юрист").param("section",section)
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.section").value(section)).andReturn();
        String id=json.readTree(result.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/java-library")).andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].title").value("Java existing"));
        mvc.perform(get("/api/java-library").param("section",section))
            .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.items[0].id").value(id));
        mvc.perform(get("/api/java-library").param("section","java").param("q","Юрист"))
            .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(content().bytes(original));
        mvc.perform(get("/api/java-library/"+id+"/cover")).andExpect(status().isOk());
        mvc.perform(multipart("/api/admin/java-library/"+id).param("title","Право — учебник").param("version","0")
            .with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.section").value(section));
        mvc.perform(multipart("/api/admin/java-library").file(pdf()).param("title","Forbidden")
            .param("section",section).with(user("reader").roles("CUSTOMER")).with(csrf()))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/java-library").param("section","other")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/admin/java-library/"+id).param("version","1")
            .with(user("admin").roles("ADMIN")).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/java-library").param("section",section)).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/java-library")).andExpect(jsonPath("$.total").value(1));
    }
    @Test void readerServesRangesAndRejectsStaleVersions() throws Exception {
        String id=create("Reader");
        String url="/api/java-library/"+id+"/content";
        mvc.perform(get(url).param("version","0")).andExpect(status().isOk())
            .andExpect(content().bytes(original)).andExpect(header().string("Accept-Ranges","bytes"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.startsWith("inline;")));
        mvc.perform(get(url).param("version","0").header("Range","bytes=0-9"))
            .andExpect(status().isPartialContent()).andExpect(content().bytes(java.util.Arrays.copyOfRange(original,0,10)));
        mvc.perform(get(url).param("version","0").header("Range","bytes=-10"))
            .andExpect(status().isPartialContent()).andExpect(content().bytes(java.util.Arrays.copyOfRange(original,original.length-10,original.length)));
        mvc.perform(get(url).param("version","0").header("Range","bytes=10-"))
            .andExpect(status().isPartialContent()).andExpect(content().bytes(java.util.Arrays.copyOfRange(original,10,original.length)));
        for(String range:new String[]{"bytes=99999999-","bytes=0-1,4-5","bytes=-0","invalid"})
            mvc.perform(get(url).param("version","0").header("Range",range)).andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(get(url).param("version","1")).andExpect(status().isConflict());
        for(String path:new String[]{"/reader.html","/reader.js","/reader.css","/pdfjs/5.6.205/build/pdf.min.mjs","/pdfjs/5.6.205/build/pdf.worker.min.mjs","/pdfjs/5.6.205/web/pdf_viewer.css"})
            mvc.perform(get(path)).andExpect(status().isOk());
        mvc.perform(get("/pdfjs/5.6.205/build/missing.mjs")).andExpect(status().isNotFound());
    }
    @Test void readerPreservesHeadersAndInvalidRangesHaveNoPdfBody() throws Exception {
        String id=create("Headers");
        String url="/api/java-library/"+id+"/content";
        mvc.perform(get(url).param("version","0")).andExpect(status().isOk())
            .andExpect(header().string("Content-Length",String.valueOf(original.length)))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andExpect(header().doesNotExist("Content-Range"))
            .andExpect(content().contentType("application/pdf"));
        for(String range:new String[]{"invalid","bytes=0-1,4-5","bytes=99999999-"}) {
            mvc.perform(get(url).param("version","0").header("Range",range))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string("Content-Range","bytes */"+original.length))
                .andExpect(header().string("Accept-Ranges","bytes"))
                .andExpect(header().doesNotExist("Content-Disposition"))
                .andExpect(content().bytes(new byte[0]));
        }
        // A stale edition takes precedence over parsing even a malformed range.
        mvc.perform(get(url).param("version","1").header("Range","invalid"))
            .andExpect(status().isConflict()).andExpect(header().doesNotExist("Accept-Ranges"));
    }

    @Test void downloadSanitizesFilenameAndKeepsUtf8AndSecurityHeaders() throws Exception {
        String id=create("Filename");
        jdbc.update("UPDATE java_library SET title=? WHERE id=?","Исаак/Сирин\\слова\r\n",id);
        var response=mvc.perform(get("/api/java-library/"+id+"/download"))
            .andExpect(status().isOk()).andExpect(content().bytes(original))
            .andExpect(header().string("Content-Length",String.valueOf(original.length)))
            .andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("X-Content-Type-Options","nosniff"))
            .andReturn().getResponse();
        var disposition=org.springframework.http.ContentDisposition.parse(response.getHeader("Content-Disposition"));
        assertThat(disposition.getType()).isEqualTo("attachment");
        assertThat(disposition.getFilename()).isEqualTo("Исаак_Сирин_слова__.pdf");
        assertThat(disposition.getCharset()).isEqualTo(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test void rangeBoundariesAndClampedEndsKeepExactBytesAcrossChunks() throws Exception {
        String id=create("Boundary");
        byte[] bytes=new byte[2*1024*1024+13];
        new java.util.Random(27).nextBytes(bytes);
        jdbc.update("DELETE FROM java_library_pdf_chunks WHERE book_id=?",id);
        for(int offset=0,part=0;offset<bytes.length;offset+=1024*1024,part++) {
            jdbc.update("INSERT INTO java_library_pdf_chunks(book_id,part,data) VALUES(?,?,?)",id,part,
                java.util.Arrays.copyOfRange(bytes,offset,Math.min(offset+1024*1024,bytes.length)));
        }
        jdbc.update("UPDATE java_library SET file_size=? WHERE id=?",bytes.length,id);
        String[] ranges={"bytes=1048575-1048576","bytes=1048576-1048580","bytes=2097152-","bytes=-13","bytes=2097150-9999999"};
        int[][] bounds={{1048575,1048577},{1048576,1048581},{2097152,bytes.length},{bytes.length-13,bytes.length},{2097150,bytes.length}};
        for(int i=0;i<ranges.length;i++) {
            int start=bounds[i][0],end=bounds[i][1];
            mvc.perform(get("/api/java-library/"+id+"/content").param("version","0").header("Range",ranges[i]))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range","bytes "+start+"-"+(end-1)+"/"+bytes.length))
                .andExpect(header().string("Content-Length",String.valueOf(end-start)))
                .andExpect(content().bytes(java.util.Arrays.copyOfRange(bytes,start,end)));
        }
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(content().bytes(bytes));
    }

    @Test void emptyStoredBlockIsRejectedByDownloadAndReader() throws Exception {
        String id=create("Empty block");
        jdbc.update("UPDATE java_library_pdf_chunks SET data=? WHERE book_id=? AND part=0",new byte[0],id);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->library.download(id,new org.springframework.mock.web.MockHttpServletResponse()))
            .isInstanceOf(IOException.class).hasMessage("Incomplete PDF storage");
        org.assertj.core.api.Assertions.assertThatThrownBy(()->library.read(id,0,null,new org.springframework.mock.web.MockHttpServletResponse()))
            .isInstanceOf(IOException.class).hasMessage("Incomplete PDF storage");
    }

    @Test void atlasAssetsArePublic() throws Exception {
        mvc.perform(get("/ai-atlas.html")).andExpect(header().string("X-Frame-Options", "SAMEORIGIN"));
        mvc.perform(get("/")).andExpect(header().string("X-Frame-Options", "DENY"));
        for (String path : new String[]{"/ai.css", "/ai-atlas.html", "/ai-atlas.css", "/ai-atlas.js", "/covers/ai-illustrations.webp"}) {
            mvc.perform(get(path)).andExpect(status().isOk());
        }
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
    @Test void largePdfIsStoredInChunksAndDownloadsUnchanged() throws Exception {
        var out=new ByteArrayOutputStream();
        try(var doc=new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            var payload=new org.apache.pdfbox.pdmodel.common.PDStream(doc);
            byte[] block=new byte[1024*1024]; new java.util.Random(1).nextBytes(block);
            try(var stream=payload.createOutputStream()) { for(int n=0;n<32;n++) stream.write(block); }
            doc.getDocumentCatalog().getCOSObject().setItem(org.apache.pdfbox.cos.COSName.getPDFName("TestPayload"),payload);
            doc.save(out);
        }
        byte[] large=out.toByteArray(); assertThat(large.length).isGreaterThan(30*1024*1024);
        var response=mvc.perform(multipart("/api/admin/java-library")
            .file(new MockMultipartFile("pdf","large.pdf","application/pdf",large))
            .param("title","Большой учебник").with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isCreated()).andReturn();
        String id=json.readTree(response.getResponse().getContentAsString()).get("id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM java_library_pdf_chunks WHERE book_id=?",Integer.class,id)).isGreaterThan(30);
        mvc.perform(get("/api/java-library/"+id+"/download")).andExpect(content().bytes(large));
        mvc.perform(get("/api/java-library/"+id+"/content").param("version","0").header("Range","bytes=1048570-1048590"))
            .andExpect(status().isPartialContent()).andExpect(content().bytes(java.util.Arrays.copyOfRange(large,1048570,1048591)));
    }

}
