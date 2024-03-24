package ru.aav.config;

import com.github.springtestdbunit.bean.DatabaseConfigBean;
import com.github.springtestdbunit.bean.DatabaseDataSourceConnectionFactoryBean;
import org.dbunit.database.DatabaseDataSourceConnection;
import org.dbunit.ext.postgresql.PostgresqlDataTypeFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@TestConfiguration
public class DSConfig {

    private static DatabaseDataSourceConnection dbUnitDatabaseConnection(String schemaName, DataSource dataSource) {
        DatabaseConfigBean dbConfig = new DatabaseConfigBean();
        dbConfig.setDatatypeFactory(new PostgresqlDataTypeFactory());
        dbConfig.setAllowEmptyFields(true);

        DatabaseDataSourceConnectionFactoryBean dbConnectionFactory =
                new DatabaseDataSourceConnectionFactoryBean(dataSource);
        dbConnectionFactory.setSchema(schemaName);
        dbConnectionFactory.setDatabaseConfig(dbConfig);

        try {
            return dbConnectionFactory.getObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Bean
    public DatabaseDataSourceConnection dbConnection(DataSource dataSource) {
        System.out.println(dataSource);
        return dbUnitDatabaseConnection("public", dataSource);
    }
}
