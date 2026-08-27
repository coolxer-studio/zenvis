package com.coolxer.configuration.datasouce;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateSettings;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Map;

/**
 * mysql 数据源
 */
@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = {"com.coolxer.dao.mysql.entity"})
@EnableJpaRepositories(
        basePackages = {"com.coolxer.dao.mysql.repository"},
        entityManagerFactoryRef = "mysqlEntityManagerFactoryBean",
        transactionManagerRef = "mysqlTransactionManager"
)
public class JpaMysqlConfiguration {

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
    @ConfigurationProperties(prefix = "spring.datasource.mysql")
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create().build();
    }

    /**
     * 插件访问 MySQL 的稳定 JDBC 契约。
     */
    @Bean(name = "pluginMysqlJdbcTemplate")
    public NamedParameterJdbcTemplate pluginMysqlJdbcTemplate() {
        return new NamedParameterJdbcTemplate(mysqlDataSource());
    }

    /**
     * 工厂Bean
     *
     * @param builder 工厂构建器
     * @return 工厂Bean
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactoryBean(EntityManagerFactoryBuilder builder) {
        return builder
                .dataSource(mysqlDataSource())
                .properties(getVendorProperties())
                .packages("com.coolxer.dao.mysql.entity")
                .persistenceUnit("mysql")
                .build();

    }

    private Map<String, Object> getVendorProperties() {
        Map<String, String> properties = jpaProperties.getProperties();

        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        return hibernateProperties.determineHibernateProperties(properties, new HibernateSettings());
    }

    @Bean(name = {"mysqlTransactionManager", "pluginMysqlTransactionManager"})
    @Primary
    public JpaTransactionManager mysqlTransactionManager(EntityManagerFactoryBuilder builder) {

        JpaTransactionManager jpaTransactionManager = new JpaTransactionManager();
        jpaTransactionManager.setEntityManagerFactory(mysqlEntityManagerFactoryBean(builder).getObject());
        return jpaTransactionManager;

    }
}
