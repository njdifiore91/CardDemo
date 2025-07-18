package com.carddemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

/**
 * Main Spring Boot application class for the CardDemo modular monolith system.
 * 
 * This application serves as the entry point for the complete credit card management
 * system, transforming 33 COBOL programs into Spring Boot microservices while 
 * maintaining identical business logic and preserving all functional requirements.
 * 
 * Architecture Overview:
 * - Modular monolith design with 8 distinct domain modules
 * - Spring Boot 3.2.x with Java 21 LTS for enterprise-grade performance
 * - PostgreSQL 15+ relational database with composite indexes replicating VSAM KSDS structures
 * - Redis-backed session management replacing CICS COMMAREA pseudo-conversational state
 * - Spring Security JWT authentication replacing RACF authentication mechanisms
 * - Containerized Spring Batch jobs replacing JCL batch processing streams
 * - React 18.x frontend with 17 components replacing BMS 3270 terminal screens
 * 
 * Domain Modules:
 * - AuthenticationService: User authentication and authorization (replacing COSGN00C)
 * - MenuService/AdminMenuService: Navigation and menu management (replacing COMEN01C/COADM01C)
 * - AccountService: Account view and update operations (replacing COACTVWC/COACTUPC)
 * - CardService: Card lifecycle management (replacing COCRDLIC/COCRDSLC/COCRDUPC)
 * - TransactionService: Transaction processing (replacing COTRN00C-02C)
 * - PaymentService: Bill payment processing (replacing COBIL00C)
 * - UserManagementService: User CRUD operations (replacing COUSR00C-03C)
 * - ReportService: Report generation (replacing CORPT00C)
 * 
 * Performance Requirements:
 * - Transaction response times ≤200ms for online operations
 * - Peak transaction capacity of 10,000 TPS
 * - Batch processing completion within 4-hour overnight windows
 * - 99.9% system availability with automatic failover capabilities
 * 
 * Data Migration:
 * - VSAM KSDS files → PostgreSQL tables with composite indexes
 * - COBOL COMP-3 fields → Java BigDecimal with exact precision preservation
 * - CICS File Control operations → Spring Data JPA repository methods
 * - JCL batch jobs → Spring Batch containerized jobs with chunk processing
 * 
 * Security Implementation:
 * - JWT token-based authentication replacing RACF user validation
 * - Role-based access control with ROLE_USER and ROLE_ADMIN authorities
 * - Spring Security method-level authorization for all REST endpoints
 * - Comprehensive audit logging through AOP interceptors
 * 
 * Integration Points:
 * - Spring Cloud Gateway for API routing and load balancing
 * - Redis session store for distributed session management
 * - PostgreSQL with HikariCP connection pooling for optimal performance
 * - Kubernetes orchestration for container lifecycle management
 * - Spring Boot Actuator for health monitoring and metrics collection
 * 
 * @author Blitzy Development Team
 * @version 1.0
 * @since 2024-01-01
 */
@SpringBootApplication
@EnableJpaRepositories
@EntityScan
@EnableBatchProcessing
@EnableScheduling
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 3600) // 1 hour session timeout
public class CardDemoApplication {
    
    /**
     * Main method to bootstrap the CardDemo Spring Boot application.
     * 
     * This method initializes the complete modular monolith system including:
     * - Spring Boot auto-configuration for all framework components
     * - PostgreSQL database connection with HikariCP connection pooling
     * - Redis session management for distributed session storage
     * - Spring Security JWT authentication and authorization
     * - Spring Batch job infrastructure for containerized processing
     * - Spring Data JPA repositories for database access
     * - Spring Cloud Gateway for API routing and cross-cutting concerns
     * - Spring Boot Actuator for comprehensive monitoring and health checks
     * 
     * The application starts with embedded Tomcat server configured for
     * high-volume transaction processing with optimized thread pool settings
     * and connection management to meet the 10,000 TPS performance requirement.
     * 
     * Environment Configuration:
     * - Development: Local PostgreSQL and Redis instances
     * - Production: PostgreSQL cluster with streaming replication
     * - Kubernetes: Orchestrated deployment with auto-scaling
     * 
     * Monitoring and Observability:
     * - Spring Boot Actuator endpoints at /actuator/*
     * - Prometheus metrics collection at /actuator/prometheus
     * - Health checks available at /actuator/health
     * - Application metrics at /actuator/metrics
     * 
     * @param args Command line arguments for application startup
     */
    public static void main(String[] args) {
    	try {
	        // Configure system properties for optimal performance
	        System.setProperty("spring.jmx.enabled", "true");
	        System.setProperty("management.endpoints.web.exposure.include", "health,info,metrics,prometheus");
	        System.setProperty("server.compression.enabled", "true");
	        System.setProperty("server.compression.mime-types", "application/json,application/xml,text/html,text/xml,text/plain");
	        
	        // Initialize Spring Boot application context
	        SpringApplication application = new SpringApplication(CardDemoApplication.class);
	        
	        // Configure additional application properties
	        application.setAdditionalProfiles("actuator", "jpa", "batch", "redis", "security");
	        
	        // Set application banner and startup logging
	        application.setBannerMode(org.springframework.boot.Banner.Mode.CONSOLE);
	        application.setLogStartupInfo(true);
	        
	        // Configure graceful shutdown for container environments
	        application.setRegisterShutdownHook(true);
	        
	        // Start the application
	        application.run(args);
    	} catch (Exception e) {
            e.printStackTrace();
        }
    }
}