package ru.aav;

import java.sql.*;
import java.util.UUID;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DigitalTechnologiesMigrationTest {
    Connection connection()throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:digital-migration-"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE","sa","");
    }
    void migrate(Connection c,String file)throws Exception {
        new Liquibase("db/migration/"+file,new ClassLoaderResourceAccessor(),new JdbcConnection(c)).update(new liquibase.Contexts());
    }
    void sections(Statement s)throws SQLException {
        s.execute("CREATE TABLE store_sections(id VARCHAR(80) PRIMARY KEY,name VARCHAR(80) NOT NULL UNIQUE,catalog_key VARCHAR(255) NOT NULL UNIQUE,library_key VARCHAR(32) NOT NULL UNIQUE,sort_order INTEGER NOT NULL DEFAULT 0,version BIGINT NOT NULL DEFAULT 0)");
    }
    String value(Statement s,String sql)throws SQLException {try(var result=s.executeQuery(sql)){result.next();return result.getString(1);}}
    @Test void formattingMigrationKeepsLegacyTextAndAddsEmptyFormatting()throws Exception {
        try(var c=connection();var s=c.createStatement()) {
            s.execute("CREATE TABLE book_editorial(id INTEGER PRIMARY KEY,description TEXT,comment TEXT)");
            s.execute("CREATE TABLE site_announcement(id INTEGER PRIMARY KEY,body TEXT)");
            s.execute("INSERT INTO book_editorial VALUES(1,'Описание с абзацами','Комментарий редактора')");
            s.execute("INSERT INTO site_announcement VALUES(1,'Прежнее объявление')");
            migrate(c,"text-formatting.xml");
            assertThat(value(s,"SELECT description || '/' || comment FROM book_editorial")).isEqualTo("Описание с абзацами/Комментарий редактора");
            assertThat(value(s,"SELECT description_style || '/' || comment_style FROM book_editorial")).isEqualTo("[]/[]");
            assertThat(value(s,"SELECT body || '/' || body_style FROM site_announcement")).isEqualTo("Прежнее объявление/[]");
        }
    }
    @Test void createsAnEmptyManagedSectionEvenInAnEmptyCatalogue()throws Exception {
        try(var c=connection();var s=c.createStatement()) {
            sections(s);migrate(c,"digital-technologies-section.xml");
            assertThat(value(s,"SELECT name FROM store_sections WHERE id='digital-technologies'")).isEqualTo("Современные цифровые технологии");
            assertThat(value(s,"SELECT sort_order FROM store_sections")).isEqualTo("10");
            migrate(c,"digital-technologies-section.xml");assertThat(value(s,"SELECT COUNT(*) FROM store_sections")).isEqualTo("1");
        }
    }
    @Test void preservesAnAdministratorCreatedSectionInsteadOfDuplicatingIt()throws Exception {
        try(var c=connection();var s=c.createStatement()) {
            sections(s);s.execute("INSERT INTO store_sections VALUES('manual','Современные цифровые технологии','section-manual','manual',35,8)");
            migrate(c,"digital-technologies-section.xml");
            assertThat(value(s,"SELECT COUNT(*) FROM store_sections")).isEqualTo("1");
            assertThat(value(s,"SELECT id || '/' || version || '/' || sort_order FROM store_sections")).isEqualTo("manual/8/35");
        }
    }
    @Test void commentUpgradePreservesExistingTextDefaultAndLongNewValues()throws Exception {
        try(var c=connection();var s=c.createStatement()) {
            s.execute("CREATE TABLE book_editorial(id INTEGER PRIMARY KEY,comment VARCHAR(1000) NOT NULL DEFAULT '')");
            s.execute("INSERT INTO book_editorial VALUES(1,'Сохранённый комментарий редактора')");
            migrate(c,"editorial-comment-text.xml");
            assertThat(value(s,"SELECT comment FROM book_editorial WHERE id=1")).isEqualTo("Сохранённый комментарий редактора");
            s.execute("INSERT INTO book_editorial(id) VALUES(2)");assertThat(value(s,"SELECT comment FROM book_editorial WHERE id=2")).isEmpty();
            String longText="Большой текст с абзацами.\n\n".repeat(600);
            try(var update=c.prepareStatement("UPDATE book_editorial SET comment=? WHERE id=2")){update.setString(1,longText);update.executeUpdate();}
            migrate(c,"editorial-comment-text.xml");assertThat(value(s,"SELECT comment FROM book_editorial WHERE id=2")).isEqualTo(longText);
        }
    }
}
