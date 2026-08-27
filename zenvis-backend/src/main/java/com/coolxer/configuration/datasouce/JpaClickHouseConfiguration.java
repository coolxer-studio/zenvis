package com.coolxer.configuration.datasouce;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

/**
 * clickHouse 数据源
 */
@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "com.coolxer.dao.clickhouse.entity")
@EnableJpaRepositories(
        basePackages = "com.coolxer.dao.clickhouse.repository",
        entityManagerFactoryRef = "clickHouseEntityManagerFactoryBean",
        transactionManagerRef = "clickHouseTransactionManager"
)
public class JpaClickHouseConfiguration {

    @Autowired
    private JpaProperties jpaProperties;
    @Autowired
    private HibernateProperties hibernateProperties;

    /**
     * 数据源
     *
     * @return 数据源
     */
    @Bean
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.clickhouse")
    public DataSource clickHouseDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 工厂Bean
     *
     * @return 工厂Bean
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean clickHouseEntityManagerFactoryBean(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(clickHouseDataSource())
                .properties(getVendorProperties())
                .packages("com.coolxer.dao.clickhouse.entity")
                .persistenceUnit("clickhouse")
                .build();

    }

    private Map<String, String> getVendorProperties() {
        Map<String, String> properties = jpaProperties.getProperties();

        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");

        return properties;
    }

    @Bean
    public JpaTransactionManager clickHouseTransactionManager(EntityManagerFactoryBuilder builder) {
        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
        jpaTransactionManager.setEntityManagerFactory(clickHouseEntityManagerFactoryBean(builder).getObject());
        return jpaTransactionManager;

    }

}
