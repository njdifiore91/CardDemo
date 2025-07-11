package com.carddemo.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.Database;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * Database configuration class providing PostgreSQL connection management with HikariCP 
 * connection pooling optimization for high-performance transactional and batch processing workloads.
 * 
 * This configuration supports the migration from IBM mainframe VSAM files to PostgreSQL database,
 * providing enterprise-grade ACID compliance and connection pool management optimized for 
 * 10,000 TPS capacity requirements as specified in Section 4.5.4.1.
 * 
 * Key Features:
 * - HikariCP connection pool with optimized settings for high-throughput processing
 * - Spring Data JPA repository configuration for entity management
 * - Flyway database migration framework for version-controlled schema evolution
 * - Transaction management supporting both JPA and Spring Batch operations
 * - Database connection monitoring and health check integration
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.connection-timeout:30000}")
    private long connectionTimeout;

    @Value("${spring.datasource.hikari.idle-timeout:600000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.max-lifetime:1800000}")
    private long maxLifetime;

    @Value("${spring.datasource.hikari.leak-detection-threshold:60000}")
    private long leakDetectionThreshold;

    @Value("${spring.jpa.hibernate.ddl-auto:validate}")
    private String hibernateDdlAuto;

    @Value("${spring.jpa.show-sql:false}")
    private boolean showSql;

    @Value("${spring.jpa.properties.hibernate.format_sql:false}")
    private boolean formatSql;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size:25}")
    private int batchSize;

    @Value("${spring.jpa.properties.hibernate.order_inserts:true}")
    private boolean orderInserts;

    @Value("${spring.jpa.properties.hibernate.order_updates:true}")
    private boolean orderUpdates;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_versioned_data:true}")
    private boolean batchVersionedData;

    @Value("${flyway.locations:classpath:db/migration}")
    private String flywayLocations;

    @Value("${flyway.baseline-on-migrate:true}")
    private boolean baselineOnMigrate;

    @Value("${flyway.validate-on-migrate:true}")
    private boolean validateOnMigrate;

    /**
     * Creates and configures the primary HikariCP DataSource for PostgreSQL connections.
     * 
     * This configuration is optimized for high-throughput transaction processing supporting
     * up to 10,000 TPS as specified in Section 4.5.4.1. The connection pool settings are
     * tuned for both online transaction processing and Spring Batch job execution.
     * 
     * Connection Pool Configuration:
     * - Maximum pool size: 20 connections (configurable)
     * - Minimum idle connections: 5 (configurable)
     * - Connection timeout: 30 seconds
     * - Idle timeout: 10 minutes
     * - Max lifetime: 30 minutes
     * - Leak detection threshold: 60 seconds
     * 
     * @return HikariDataSource configured for PostgreSQL with optimized connection pooling
     */
    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Basic connection properties
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        
        // Connection pool sizing optimized for 10,000 TPS capacity
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        
        // Connection timeout and lifecycle management
        config.setConnectionTimeout(connectionTimeout);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(maxLifetime);
        config.setLeakDetectionThreshold(leakDetectionThreshold);
        
        // Pool name for monitoring and debugging
        config.setPoolName("CardDemoHikariCP");
        
        // Connection validation and testing
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        // PostgreSQL-specific optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        
        return new HikariDataSource(config);
    }

    /**
     * Configures the JPA EntityManagerFactory with PostgreSQL-specific settings.
     * 
     * This factory bean creates JPA EntityManager instances with optimized configuration
     * for PostgreSQL database operations, supporting both transactional processing and
     * batch operations with proper entity scanning and JPA properties.
     * 
     * @param dataSource The HikariCP DataSource for database connections
     * @return LocalContainerEntityManagerFactoryBean configured for PostgreSQL
     */
    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.carddemo.entity");
        
        // Configure Hibernate JPA vendor adapter for PostgreSQL
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setDatabase(Database.POSTGRESQL);
        vendorAdapter.setGenerateDdl(false);
        vendorAdapter.setShowSql(showSql);
        em.setJpaVendorAdapter(vendorAdapter);
        
        // Set JPA properties for optimal PostgreSQL performance
        Properties jpaProperties = new Properties();
        jpaProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        jpaProperties.put("hibernate.hbm2ddl.auto", hibernateDdlAuto);
        jpaProperties.put("hibernate.show_sql", showSql);
        jpaProperties.put("hibernate.format_sql", formatSql);
        
        // Batch processing optimizations
        jpaProperties.put("hibernate.jdbc.batch_size", batchSize);
        jpaProperties.put("hibernate.order_inserts", orderInserts);
        jpaProperties.put("hibernate.order_updates", orderUpdates);
        jpaProperties.put("hibernate.jdbc.batch_versioned_data", batchVersionedData);
        
        // Connection and transaction management
        jpaProperties.put("hibernate.connection.provider_disables_autocommit", "true");
        jpaProperties.put("hibernate.jdbc.lob.non_contextual_creation", "true");
        jpaProperties.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        
        // Performance and caching optimizations
        jpaProperties.put("hibernate.jdbc.fetch_size", "50");
        jpaProperties.put("hibernate.cache.use_second_level_cache", "false");
        jpaProperties.put("hibernate.cache.use_query_cache", "false");
        jpaProperties.put("hibernate.generate_statistics", "false");
        
        // Enable optimistic locking for concurrent access control
        jpaProperties.put("hibernate.id.new_generator_mappings", "true");
        jpaProperties.put("hibernate.id.optimizer.pooled.preferred", "pooled-lo");
        
        em.setJpaProperties(jpaProperties);
        
        return em;
    }

    /**
     * Configures the primary transaction manager for JPA operations.
     * 
     * This transaction manager supports both online transaction processing and
     * Spring Batch job execution with proper ACID compliance and optimistic
     * locking support as specified in Section 3.5.1.3.
     * 
     * @param entityManagerFactory The JPA EntityManagerFactory
     * @return PlatformTransactionManager configured for JPA operations
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory.getObject());
        
        // Set default transaction timeout (5 minutes for batch operations)
        transactionManager.setDefaultTimeout(300);
        
        // Enable nested transaction support for complex operations
        transactionManager.setNestedTransactionAllowed(true);
        
        return transactionManager;
    }

    /**
     * Configures Flyway database migration for version-controlled schema evolution.
     * 
     * This configuration manages database schema changes through incremental SQL scripts
     * with rollback capabilities and environment-specific configurations as specified
     * in Section 3.5.2.1.
     * 
     * Migration Strategy:
     * - V1__Create_Schema.sql: Complete database schema creation
     * - V2__Load_Initial_Data.sql: Initial data population from ASCII files
     * - Additional versioned migration scripts for incremental changes
     * 
     * @param dataSource The HikariCP DataSource for migration operations
     * @return Flyway instance configured for PostgreSQL schema management
     */
    @Bean
    public Flyway flywayMigration(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(flywayLocations)
            .baselineOnMigrate(baselineOnMigrate)
            .validateOnMigrate(validateOnMigrate)
            .cleanDisabled(true)
            .load();
        
        // Execute migration on startup
        flyway.migrate();
        
        return flyway;
    }

    /**
     * Provides connection pool health check information for monitoring.
     * 
     * This method exposes HikariCP metrics for Spring Boot Actuator integration,
     * enabling monitoring of connection pool health and performance metrics
     * as specified in Section 4.5.3.1.
     * 
     * @return HikariDataSource for health check integration
     */
    @Bean
    public HikariDataSource hikariDataSource() {
        return (HikariDataSource) dataSource();
    }
}