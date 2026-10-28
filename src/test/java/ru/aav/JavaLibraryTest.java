package ru.aav;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:javalibrary;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password="})
@AutoConfigureMockMvc
class JavaLibraryTest extends JavaLibraryContract {}
