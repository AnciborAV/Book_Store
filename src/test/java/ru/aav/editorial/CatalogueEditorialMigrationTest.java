package ru.aav.editorial;

import com.fasterxml.jackson.databind.ObjectMapper;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class CatalogueEditorialMigrationTest {
    record Book(String title,String author) {}
    record Alias(String old_title,String old_author,String new_title,String new_author) {}
    private void migrate(Connection c) throws Exception {
        new Liquibase("db/migration/catalogue-editorial-20261031.xml",new ClassLoaderResourceAccessor(),new JdbcConnection(c))
            .update(new liquibase.Contexts());
    }
    protected Connection connection() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:editorial-migration;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","sa","");
    }
    @Test void fillsOnlyEmptyDescriptionsAndPreservesFilesOrderAndAdminEdits() throws Exception {
        try(var c=connection()) {
            var s=c.createStatement();
            s.execute("CREATE TABLE books(id BIGINT PRIMARY KEY,name VARCHAR(255),author VARCHAR(255),archived BOOLEAN DEFAULT FALSE,cover_title VARCHAR(255),management_revision BIGINT DEFAULT 0,price DECIMAL(10,2),sort_order INTEGER)");
            s.execute("CREATE TABLE java_library(id VARCHAR(36) PRIMARY KEY,title VARCHAR(255),author VARCHAR(255),description VARCHAR(3000),version BIGINT DEFAULT 0)");
            s.execute("CREATE TABLE book_editorial(id BIGSERIAL PRIMARY KEY,catalog_id BIGINT UNIQUE,library_id VARCHAR(36) UNIQUE,description VARCHAR(3000),version BIGINT DEFAULT 0,lists VARCHAR(500) DEFAULT '',comment VARCHAR(1000) DEFAULT '')");
            s.execute("CREATE TABLE catalog_pdf(book_id BIGINT PRIMARY KEY,library_id VARCHAR(36))");
            var fixture=new ObjectMapper().readValue(getClass().getResourceAsStream("/editorial/catalogue-20261031.json"),Book[].class);
            assertThat(fixture).hasSize(171);
            try(var insert=c.prepareStatement("INSERT INTO books(id,name,author,price,sort_order) VALUES(?,?,?,125,?)")) {
                int id=0;for(var b:fixture){insert.setInt(1,++id);insert.setString(2,b.title());insert.setString(3,b.author());insert.setInt(4,id);insert.executeUpdate();}
            }
            s.execute("INSERT INTO book_editorial(catalog_id,description,lists,comment,version) VALUES(1,'Моё описание','Избранное','Комментарий',7),(2,'  ' || CHR(10) || CHR(9),'Подборка','Заметка',3)");
            s.execute("INSERT INTO java_library(id,title,author,description) VALUES('original','Евгений Онегин','А.С.Пушкин','Исходное описание PDF'),('empty','Жизнь Арсентьева','И.А.Бунинин',''),('linked','Евгений Онегин','А.С.Пушкин',''),('blank','Блаженный Августин','Исповедь','')");
            // A similarly named author must not accidentally receive a correction or description.
            s.execute("INSERT INTO catalog_pdf VALUES(1,'linked')");
            migrate(c);
            assertThat(value(s,"SELECT description FROM book_editorial WHERE catalog_id=1")).isEqualTo("Моё описание");
            assertThat(value(s,"SELECT lists || '/' || comment || '/' || version FROM book_editorial WHERE catalog_id=1")).isEqualTo("Избранное/Комментарий/7");
            assertThat(value(s,"SELECT lists || '/' || comment || '/' || version FROM book_editorial WHERE catalog_id=2")).isEqualTo("Подборка/Заметка/4");
            assertThat(value(s,"SELECT COUNT(*) FROM book_editorial WHERE catalog_id IS NOT NULL")).isEqualTo("147");
            assertThat(value(s,"SELECT COUNT(*) FROM book_editorial WHERE library_id IN ('original','empty','linked')")).isEqualTo("0");
            assertThat(value(s,"SELECT title || '/' || author FROM java_library WHERE id='blank'")).isEqualTo("Исповедь/Августин Блаженный");
            assertThat(value(s,"SELECT LENGTH(description)>300 FROM book_editorial WHERE library_id='blank'")).isEqualTo("TRUE");
            assertThat(value(s,"SELECT COUNT(*) FROM books WHERE price=125 AND sort_order=id")).isEqualTo("171");
            assertThat(value(s,"SELECT library_id FROM catalog_pdf WHERE book_id=1")).isEqualTo("linked");
            assertThat(value(s,"SELECT cover_title FROM books WHERE name='Жизнь Арсеньева'")).isEqualTo("Жизнь Арсентьева");
            assertThat(value(s,"SELECT author FROM books WHERE name LIKE 'Наследственное право:%'")).isEqualTo("Евгений Юрьевич Петров (отв. ред.)");
            assertThat(value(s,"SELECT title FROM java_library WHERE id='empty'")).isEqualTo("Жизнь Арсентьева");
            s.execute("UPDATE book_editorial SET description='' WHERE catalog_id=2");
            migrate(c);
            assertThat(value(s,"SELECT description FROM book_editorial WHERE catalog_id=2")).isEmpty();
        }
    }
    private String value(Statement s,String sql) throws SQLException {try(var r=s.executeQuery(sql)){r.next();return r.getString(1);}}
    @Test void correctedJavaNamesResolveToUnchangedDescriptions() throws Exception {
        var aliases=new ObjectMapper().readValue(getClass().getResourceAsStream("/editorial/java-metadata-aliases.json"),Alias[].class);
        for(var a:aliases) {
            var before=BookDescriptions.find(a.old_title(),a.old_author());
            assertThat(before.text()).isNotBlank();
            assertThat(BookDescriptions.find(a.new_title(),a.new_author())).isEqualTo(before);
        }
    }
}
