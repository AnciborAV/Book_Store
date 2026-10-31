package ru.aav.editorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.*;

/** Reviewed profiles use exact author credits, never fuzzy name or title matching. */
@Component
public final class AuthorProfiles {
    public record Portrait(String path,String caption,String credit,String source,String license,String licenseUrl,int width,int height) {}
    public record Author(String id,String name,String dates,String biography,Portrait portrait) {}
    public record Detail(List<Author> authors,String bookNote) {}
    private record Entry(String id,String name,String dates,String biography,Portrait portrait,List<String> sources) {}
    private record Credit(String title,String author,List<String> authors,String note) {}
    private record Data(List<Entry> profiles,List<Credit> books) {}
    private final Map<String,Author> profiles;
    private final Map<String,List<String>> credits;
    private final Map<String,String> notes;
    private final Map<String,List<String>> bookCredits;

    public AuthorProfiles(ObjectMapper mapper) {
        try(var stream=getClass().getResourceAsStream("/editorial/author-profiles.json")) {
            if(stream==null)throw new IllegalStateException("Missing author profiles");
            var data=mapper.readValue(stream,Data.class);
            var people=new LinkedHashMap<String,Author>();
            var names=new HashMap<String,List<String>>();
            var bookNotes=new HashMap<String,String>();
            var exact=new HashMap<String,List<String>>();
            var ambiguous=new HashSet<String>();
            for(var e:data.profiles()) {
                if(e.id()==null || e.name()==null || e.biography()==null || e.biography().isBlank())
                    throw new IllegalStateException("Invalid author profile");
                if(people.putIfAbsent(e.id(),new Author(e.id(),e.name(),e.dates(),e.biography(),e.portrait()))!=null)
                    throw new IllegalStateException("Duplicate author profile: "+e.id());
                names.put(key(e.name()),List.of(e.id()));
            }
            for(var b:data.books()) {
                if(b.authors().isEmpty() || !people.keySet().containsAll(b.authors()))
                    throw new IllegalStateException("Unknown author credit: "+b.title());
                var ids=List.copyOf(b.authors());
                var prior=exact.putIfAbsent(bookKey(b.title(),b.author()),ids);
                if(prior!=null && !prior.equals(ids))
                    throw new IllegalStateException("Conflicting book credit: "+b.title());
                var old=names.putIfAbsent(key(b.author()),ids);
                if(old!=null && !old.equals(ids))ambiguous.add(key(b.author()));
                bookNotes.put(bookKey(b.title(),b.author()),Objects.requireNonNullElse(b.note(),""));
            }
            ambiguous.forEach(names::remove);
            profiles=Map.copyOf(people);credits=Map.copyOf(names);notes=Map.copyOf(bookNotes);bookCredits=Map.copyOf(exact);
        } catch(IOException ex) {throw new IllegalStateException("Cannot read author profiles",ex);}
    }
    public Detail find(String title,String author) {
        var ids=bookCredits.getOrDefault(bookKey(title,author),credits.getOrDefault(key(author),List.of()));
        return new Detail(ids.stream().map(profiles::get).toList(),notes.getOrDefault(bookKey(title,author),""));
    }
    private static String key(String s){return Objects.requireNonNullElse(s,"").strip().toLowerCase(Locale.ROOT);}
    private static String bookKey(String title,String author){return key(title)+"\n"+key(author);}
}
