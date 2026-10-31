package ru.aav.text;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class TextStylesTest {
 final TextStyles styles=new TextStyles(new ObjectMapper());
 TextStyles.Mark mark(int a,int b){return new TextStyles.Mark(a,b,true,false,true,"#d6b576",24);}
 @Test void trimsOffsetsAndRoundTripsOnlyStructuredAttributes(){
  var expected=List.of(mark(0,5));var actual=styles.resolve("  hello  ",List.of(mark(2,7)),"",List.of());
  assertThat(actual).isEqualTo(expected);assertThat(styles.read(styles.write(actual))).isEqualTo(expected);
 }
 @Test void legacyClientsKeepFormattingOnlyWhenTextIsUnchanged(){
  var previous=List.of(mark(0,5));assertThat(styles.resolve("hello",null,"hello",previous)).isEqualTo(previous);
  assertThat(styles.resolve("different",null,"hello",previous)).isEmpty();assertThat(styles.resolve("hello",List.of(),"hello",previous)).isEmpty();
 }
 @Test void invalidRangesAndUnsafeValuesAreRejected(){
  for(var marks:List.of(List.of(mark(0,9)),List.of(mark(0,3),mark(2,4)),List.of(new TextStyles.Mark(0,1,false,false,false,"url(javascript:alert(1))",16)),List.of(new TextStyles.Mark(0,1,false,false,false,null,100))))
   assertThatThrownBy(()->styles.resolve("hello",marks,"",List.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
 @Test void unicodeSelectionCannotSplitASurrogatePair(){
  assertThat(styles.resolve("😀x",List.of(mark(0,2)),"",List.of())).hasSize(1);
  assertThatThrownBy(()->styles.resolve("😀x",List.of(mark(1,2)),"",List.of())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
 }
}
