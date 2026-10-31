package ru.aav.editorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.assertThat;

class AuthorProfilesTest {
    private final ObjectMapper json=new ObjectMapper();
    private final AuthorProfiles registry=new AuthorProfiles(json);
    record Book(String title,String author) {}
    @Test void everyLiveBookAndEveryCoauthorHasAReviewedProfile() throws Exception {
        var books=json.readValue(getClass().getResourceAsStream("/editorial/author-catalogue-20261031.json"),Book[].class);
        assertThat(books).hasSize(171);
        var ids=new HashSet<String>();
        for(var b:books) {
            var detail=registry.find(b.title(),b.author());
            assertThat(detail.authors()).as(b.title()).hasSize(b.author().split(",").length);
            for(var a:detail.authors()) {
                ids.add(a.id());assertThat(a.biography()).hasSizeGreaterThan(400).doesNotContain("—","–","\u00ad","http");
                assertThat(a.biography().split("\n\n")).hasSizeGreaterThanOrEqualTo(2);
                assertThat(a.dates()).isNotBlank();
                assertThat(a.portrait()).as(a.name()).isNotNull();
                if(a.portrait()!=null) {
                    var p=a.portrait();assertThat(p.path()).startsWith("/covers/authors/").doesNotContain("..");
                    try(var stream=getClass().getResourceAsStream("/static"+p.path())) {
                        assertThat(stream).as(a.name()).isNotNull();
                        byte[] bytes=stream.readAllBytes();assertThat(bytes.length).isLessThan(100_000);
                        var image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));assertThat(image).isNotNull();
                        assertThat(image.getWidth()).isEqualTo(p.width()).isLessThanOrEqualTo(400);
                        assertThat(image.getHeight()).isEqualTo(p.height()).isLessThanOrEqualTo(500);
                    }
                    assertThat(p.source()).startsWith("https://");assertThat(p.credit()).isNotBlank();assertThat(p.license()).isNotBlank();
                }
            }
        }
        assertThat(ids).hasSize(116);
    }
    @Test void exactLegacyCreditsDoNotConfuseEditorsOrNamesakes() {
        assertThat(registry.find("Наследственное право ст.1110-1185","А.Г.Карапетов").authors()).extracting(AuthorProfiles.Author::name).containsExactly("Евгений Юрьевич Петров");
        assertThat(registry.find("Аренда ст. 606-625 и 650-655","А.Г.Карапетов").authors()).extracting(AuthorProfiles.Author::name).containsExactly("Артём Георгиевич Карапетов");
        assertThat(registry.find("Новая книга","Неизвестный автор").authors()).isEmpty();
        assertThat(registry.find("Новая книга","Кевин Мёрфи").authors().getFirst().biography()).contains("вероятностным моделированием").doesNotContain("политик");
        assertThat(registry.find("Новая книга","Марсель Пруст").bookNote()).isEmpty();
    }
}
