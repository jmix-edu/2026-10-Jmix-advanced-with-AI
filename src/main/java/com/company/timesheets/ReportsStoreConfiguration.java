package com.company.timesheets;

import io.jmix.autoconfigure.data.JmixLiquibaseCreator;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.boot.liquibase.autoconfigure.LiquibaseProperties;
import io.jmix.core.JmixModules;
import io.jmix.core.Resources;
import io.jmix.data.impl.JmixEntityManagerFactoryBean;
import io.jmix.data.impl.JmixTransactionManager;
import io.jmix.data.persistence.DbmsSpecifics;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import jakarta.persistence.EntityManagerFactory;

import javax.sql.DataSource;

@Configuration
public class ReportsStoreConfiguration {

    @Bean
    @ConfigurationProperties("reports.datasource")
    DataSourceProperties reportsDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "reports.datasource.hikari")
    DataSource reportsDataSource(@Qualifier("reportsDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    LocalContainerEntityManagerFactoryBean reportsEntityManagerFactory(
            @Qualifier("reportsDataSource") DataSource dataSource,
            JpaVendorAdapter jpaVendorAdapter,
            DbmsSpecifics dbmsSpecifics,
            JmixModules jmixModules,
            Resources resources
    ) {
        return new JmixEntityManagerFactoryBean("reports", dataSource, jpaVendorAdapter, dbmsSpecifics, jmixModules, resources);
    }

    @Bean
    JpaTransactionManager reportsTransactionManager(@Qualifier("reportsEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JmixTransactionManager("reports", entityManagerFactory);
    }

    @Bean("reportsLiquibaseProperties")
    @ConfigurationProperties(prefix = "reports.liquibase")
    public LiquibaseProperties reportsLiquibaseProperties() {
        return new LiquibaseProperties();
    }

    @Bean("reportsLiquibase")
    public SpringLiquibase reportsLiquibase(@Qualifier("reportsDataSource") DataSource dataSource,
                                            @Qualifier("reportsLiquibaseProperties") LiquibaseProperties liquibaseProperties) {
        return JmixLiquibaseCreator.create(dataSource, liquibaseProperties);
    }
}
