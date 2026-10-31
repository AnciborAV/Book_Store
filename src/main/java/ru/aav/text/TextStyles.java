package ru.aav.text;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Formatting is data, never HTML. Offsets use UTF-16, as in browser selections. */
@Component
@RequiredArgsConstructor
public class TextStyles {
    private final ObjectMapper json;
    public record Mark(@Min(0) int start,@Min(1) int end,boolean bold,boolean italic,boolean underline,
                       @Pattern(regexp="#[0-9a-fA-F]{6}") String color,@Min(12) @Max(32) Integer size) {}
    public List<Mark> read(String value) {
        if(value==null||value.isBlank())return List.of();
        try {return List.of(json.readValue(value,Mark[].class));}
        catch(Exception e){throw new IllegalStateException("Invalid stored text formatting",e);}
    }
    public String write(List<Mark> value) {
        try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}
    }
    public List<Mark> resolve(String input,List<Mark> requested,String previous,List<Mark> old) {
        if(requested==null)return input.strip().equals(previous)?old:List.of();
        if(requested.size()>5000)throw invalid();
        String stripped=input.strip();int offset=stripped.isEmpty()?0:input.indexOf(stripped),limit=offset+stripped.length(),end=0;
        var result=new ArrayList<Mark>();
        for(var m:requested) {
            if(m==null||m.start()<end||m.start()<0||m.end()<=m.start()||m.end()>input.length()||
               (m.color()!=null&&!m.color().matches("#[0-9a-fA-F]{6}"))||
               (m.size()!=null&&(m.size()<12||m.size()>32))||splitsSurrogate(input,m.start())||splitsSurrogate(input,m.end()))throw invalid();
            end=m.end();int a=Math.max(m.start(),offset),b=Math.min(m.end(),limit);
            if(a<b&&(m.bold()||m.italic()||m.underline()||m.color()!=null||m.size()!=null))
                result.add(new Mark(a-offset,b-offset,m.bold(),m.italic(),m.underline(),m.color(),m.size()));
        }
        return List.copyOf(result);
    }
    private boolean splitsSurrogate(String text,int at){return at>0&&at<text.length()&&Character.isHighSurrogate(text.charAt(at-1))&&Character.isLowSurrogate(text.charAt(at));}
    private ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Некорректное оформление текста. Откройте редактор заново.");}
}
