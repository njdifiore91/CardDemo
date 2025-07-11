package com.carddemo.batch;

import com.carddemo.config.DatabaseConfig;
import com.carddemo.audit.AuditService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionException;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.repository.dao.Jackson2ExecutionContextStringSerializer;
import org.springframework.batch.core.repository.support.JobRepositoryFactoryBean;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.explore.support.JobExplorerFactoryBean;
import org.springframework.batch.core.step.skip.SkipPolicy;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;

/**
 * Common Spring Batch infrastructure configuration providing shared job repository, 
 * transaction management, error handling, and monitoring capabilities for all batch 
 * processing jobs in the CardDemo application.
 * 
 * This configuration enables comprehensive batch processing infrastructure to replace
 * traditional mainframe JCL job execution with modern Spring Batch capabilities.
 * Features include PostgreSQL-based job persistence, enterprise-grade error handling,
 * retry mechanisms, audit logging, and Kubernetes CronJob integration.
 * 
 * Key Features:
 * - PostgreSQL JobRepository for job execution tracking and restart capabilities
 * - Common error handling strategies with retry policies and skip logic
 * - Comprehensive monitoring and audit logging for all batch operations
 * - HikariCP connection pool optimization for batch processing workloads
 * - Kubernetes CronJob integration for containerized batch execution
 * - Enterprise-grade transaction management with configurable isolation levels
 * - Performance monitoring and metrics collection for SLA compliance
 * - Automatic job recovery and restart capabilities
 * 
 * Architecture Integration:
 * - Integrates with DatabaseConfig for optimized PostgreSQL connectivity
 * - Leverages AuditService for comprehensive batch job audit trail
 * - Supports Spring Security context propagation for authenticated batch jobs
 * - Provides common infrastructure for all batch job implementations
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Configuration
@EnableBatchProcessing
public class BatchJobConfig {

    private static final Logger logger = LoggerFactory.getLogger(BatchJobConfig.class);

    @Autowired
    private DatabaseConfig databaseConfig;

    @Autowired
    private AuditService auditService;

    // Batch processing configuration properties
    @Value("${spring.batch.job.enabled:true}")
    private boolean batchJobEnabled;

    @Value("${spring.batch.initialize-schema:embedded}")
    private String initializeSchema;

    @Value("${app.batch.core-pool-size:4}")
    private int corePoolSize;

    @Value("${app.batch.max-pool-size:8}")
    private int maxPoolSize;

    @Value("${app.batch.queue-capacity:100}")
    private int queueCapacity;

    @Value("${app.batch.thread-name-prefix:batch-}")
    private String threadNamePrefix;

    @Value("${app.batch.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${app.batch.retry.initial-interval:1000}")
    private long initialRetryInterval;

    @Value("${app.batch.retry.max-interval:10000}")
    private long maxRetryInterval;

    @Value("${app.batch.retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${app.batch.skip.max-count:10}")
    private int maxSkipCount;

    @Value("${app.batch.chunk-size:100}")
    private int chunkSize;

    @Value("${app.batch.job-timeout:3600}")
    private int jobTimeoutSeconds;

    /**
     * Configures the PostgreSQL-based JobRepository for Spring Batch job execution tracking.
     * 
     * This JobRepository provides persistent storage for batch job metadata, execution status,
     * and restart capabilities. It replaces traditional JCL job tracking mechanisms with
     * enterprise-grade PostgreSQL persistence supporting high-concurrency batch processing.
     * 
     * Features:
     * - PostgreSQL persistence with optimized database schema
     * - Job execution tracking with comprehensive metadata storage
     * - Restart capabilities for failed or interrupted jobs
     * - Concurrent job execution support with proper isolation
     * - Performance optimization for high-volume batch processing
     * - Integration with HikariCP connection pooling
     * 
     * @return JobRepository configured for PostgreSQL persistence
     * @throws Exception if JobRepository configuration fails
     */
    @Bean
    public JobRepository jobRepository() throws Exception {
        logger.info("Configuring Spring Batch JobRepository with PostgreSQL persistence");
        
        JobRepositoryFactoryBean factory = new JobRepositoryFactoryBean();
        
        // Configure database connection using optimized HikariCP DataSource
        factory.setDataSource(databaseConfig.dataSource());
        factory.setTransactionManager(databaseConfig.transactionManager(databaseConfig.entityManagerFactory(databaseConfig.dataSource())));
        
        // Set database type and schema initialization
        factory.setDatabaseType("POSTGRES");
        factory.setTablePrefix("BATCH_");
        factory.setIsolationLevelForCreate("ISOLATION_READ_COMMITTED");
        
        // Configure job execution context serialization
        factory.setSerializer(new Jackson2ExecutionContextStringSerializer());
        
        // Enable automatic schema validation
        factory.setValidateTransactionState(true);
        
        // Configure job repository timeout settings
        factory.setMaxVarCharLength(2500);
        factory.setLobHandler(null);
        
        // Apply after properties set to initialize factory
        factory.afterPropertiesSet();
        
        JobRepository jobRepository = factory.getObject();
        
        // Log successful configuration
        logger.info("JobRepository configured successfully with PostgreSQL persistence");
        
        // Audit log the configuration event
        auditService.logSecurityEvent("BATCH_INFRASTRUCTURE_CONFIGURED", "INFO", 
                                     "JobRepository initialized with PostgreSQL persistence", 
                                     Map.of("database_type", "POSTGRESQL", 
                                           "table_prefix", "BATCH_",
                                           "isolation_level", "READ_COMMITTED"));
        
        return jobRepository;
    }

    /**
     * Configures the JobExplorer for read-only access to batch job execution history.
     * 
     * This JobExplorer provides comprehensive querying capabilities for batch job
     * execution history, status monitoring, and performance metrics essential for
     * batch job monitoring and SLA compliance tracking.
     * 
     * Features:
     * - Read-only access to job execution metadata
     * - Job execution history querying capabilities
     * - Performance metrics and monitoring integration
     * - Job instance and execution status tracking
     * - Support for job restart decision making
     * 
     * @return JobExplorer configured for PostgreSQL queries
     * @throws Exception if JobExplorer configuration fails
     */
    @Bean
    public JobExplorer jobExplorer() throws Exception {
        logger.info("Configuring Spring Batch JobExplorer for execution history queries");
        
        JobExplorerFactoryBean factory = new JobExplorerFactoryBean();
        
        // Configure database connection for read-only access
        factory.setDataSource(databaseConfig.dataSource());
        factory.setTablePrefix("BATCH_");
        
        // Set database type and serialization
        factory.setSerializer(new Jackson2ExecutionContextStringSerializer());
        
        // Apply after properties set to initialize factory
        factory.setTransactionManager(databaseConfig.transactionManager(databaseConfig.entityManagerFactory(databaseConfig.dataSource())));
        factory.afterPropertiesSet();
        
        JobExplorer jobExplorer = factory.getObject();
        
        // Log successful configuration
        logger.info("JobExplorer configured successfully for batch job monitoring");
        
        return jobExplorer;
    }

    /**
     * Configures the JobLauncher for programmatic batch job execution.
     * 
     * This JobLauncher provides asynchronous job execution capabilities optimized
     * for Kubernetes CronJob integration and manual job triggering with comprehensive
     * parameter support and execution context management.
     * 
     * Features:
     * - Asynchronous job execution with thread pool management
     * - Kubernetes CronJob integration capabilities
     * - Job parameter validation and processing
     * - Execution context propagation and management
     * - Comprehensive error handling and logging
     * - Performance monitoring and metrics collection
     * 
     * @param jobRepository The configured JobRepository for job persistence
     * @return JobLauncher configured for asynchronous execution
     * @throws Exception if JobLauncher configuration fails
     */
    @Bean
    public JobLauncher jobLauncher(JobRepository jobRepository) throws Exception {
        logger.info("Configuring Spring Batch JobLauncher for asynchronous execution");
        
        TaskExecutorJobLauncher jobLauncher = new TaskExecutorJobLauncher();
        
        // Configure job repository for execution tracking
        jobLauncher.setJobRepository(jobRepository);
        
        // Configure task executor for asynchronous execution
        jobLauncher.setTaskExecutor(batchTaskExecutor());
        
        // Apply after properties set to initialize launcher
        jobLauncher.afterPropertiesSet();
        
        // Log successful configuration
        logger.info("JobLauncher configured successfully for asynchronous batch execution");
        
        return jobLauncher;
    }

    /**
     * Configures a common StepExecutionListener for comprehensive step-level monitoring.
     * 
     * This listener provides detailed monitoring and audit logging for each step
     * execution within batch jobs, capturing performance metrics, error conditions,
     * and execution context for comprehensive batch processing observability.
     * 
     * Features:
     * - Step execution lifecycle monitoring
     * - Performance metrics collection and logging
     * - Error condition capture and reporting
     * - Audit trail generation for compliance
     * - Integration with enterprise monitoring systems
     * 
     * @return StepExecutionListener for common step monitoring
     */
    @Bean
    public StepExecutionListener commonStepListener() {
        logger.info("Configuring common StepExecutionListener for batch step monitoring");
        
        return new StepExecutionListener() {
            @Override
            public void beforeStep(StepExecution stepExecution) {
                String stepName = stepExecution.getStepName();
                String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
                
                logger.info("Starting batch step: {} in job: {}", stepName, jobName);
                
                // Capture step start time for performance tracking
                stepExecution.getExecutionContext().putLong("step_start_time", System.currentTimeMillis());
                
                // Log step execution audit event
                String username = getCurrentUsername();
                auditService.logNavigationEvent(username, "BATCH_JOB_STEP_START", 
                                              stepName, "STEP_EXECUTION");
            }
            
            @Override
            public ExitStatus afterStep(StepExecution stepExecution) {
                String stepName = stepExecution.getStepName();
                String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
                
                // Calculate step execution duration
                long startTime = stepExecution.getExecutionContext().getLong("step_start_time", 0L);
                long duration = System.currentTimeMillis() - startTime;
                
                // Log step completion with performance metrics
                logger.info("Completed batch step: {} in job: {} - Duration: {}ms, " +
                           "Read: {}, Written: {}, Skipped: {}, Committed: {}", 
                           stepName, jobName, duration,
                           stepExecution.getReadCount(),
                           stepExecution.getWriteCount(),
                           stepExecution.getSkipCount(),
                           stepExecution.getCommitCount());
                
                // Audit log step completion
                String username = getCurrentUsername();
                Map<String, Object> stepDetails = new HashMap<>();
                stepDetails.put("step_name", stepName);
                stepDetails.put("job_name", jobName);
                stepDetails.put("duration_ms", duration);
                stepDetails.put("read_count", stepExecution.getReadCount());
                stepDetails.put("write_count", stepExecution.getWriteCount());
                stepDetails.put("skip_count", stepExecution.getSkipCount());
                stepDetails.put("commit_count", stepExecution.getCommitCount());
                stepDetails.put("exit_status", stepExecution.getExitStatus().getExitCode());
                
                auditService.logTransactionEvent(username, "BATCH_STEP_COMPLETED", 
                                                stepExecution.getId().toString(), 
                                                (double) duration, stepDetails);
                
                return stepExecution.getExitStatus();
            }
        };
    }

    /**
     * Configures a common JobExecutionListener for comprehensive job-level monitoring.
     * 
     * This listener provides detailed monitoring and audit logging for entire batch
     * job executions, capturing job lifecycle events, performance metrics, and
     * execution outcomes for comprehensive batch processing observability.
     * 
     * Features:
     * - Job execution lifecycle monitoring
     * - Performance metrics collection and reporting
     * - Error condition capture and analysis
     * - Audit trail generation for compliance reporting
     * - Integration with enterprise monitoring and alerting systems
     * 
     * @return JobExecutionListener for common job monitoring
     */
    @Bean
    public JobExecutionListener commonJobListener() {
        logger.info("Configuring common JobExecutionListener for batch job monitoring");
        
        return new JobExecutionListener() {
            @Override
            public void beforeJob(JobExecution jobExecution) {
                String jobName = jobExecution.getJobInstance().getJobName();
                Long jobExecutionId = jobExecution.getId();
                
                logger.info("Starting batch job: {} with execution ID: {}", jobName, jobExecutionId);
                
                // Capture job start time for performance tracking
                jobExecution.getExecutionContext().putLong("job_start_time", System.currentTimeMillis());
                
                // Log job start audit event
                String username = getCurrentUsername();
                Map<String, Object> jobDetails = new HashMap<>();
                jobDetails.put("job_name", jobName);
                jobDetails.put("job_execution_id", jobExecutionId);
                jobDetails.put("job_parameters", jobExecution.getJobParameters().toString());
                jobDetails.put("start_time", LocalDateTime.now().toString());
                
                auditService.logAdminMenuAccess(username, "BATCH_JOB_START", 
                                              "BATCH_ADMIN", "GRANTED");
                
                auditService.logTransactionEvent(username, "BATCH_JOB_STARTED", 
                                                jobExecutionId.toString(), 
                                                0.0, jobDetails);
            }
            
            @Override
            public void afterJob(JobExecution jobExecution) {
                String jobName = jobExecution.getJobInstance().getJobName();
                Long jobExecutionId = jobExecution.getId();
                
                // Calculate job execution duration
                long startTime = jobExecution.getExecutionContext().getLong("job_start_time", 0L);
                long duration = System.currentTimeMillis() - startTime;
                
                // Log job completion with comprehensive metrics
                logger.info("Completed batch job: {} with execution ID: {} - " +
                           "Duration: {}ms, Status: {}, Exit Code: {}", 
                           jobName, jobExecutionId, duration,
                           jobExecution.getStatus(), 
                           jobExecution.getExitStatus().getExitCode());
                
                // Audit log job completion
                String username = getCurrentUsername();
                Map<String, Object> jobDetails = new HashMap<>();
                jobDetails.put("job_name", jobName);
                jobDetails.put("job_execution_id", jobExecutionId);
                jobDetails.put("duration_ms", duration);
                jobDetails.put("status", jobExecution.getStatus().toString());
                jobDetails.put("exit_code", jobExecution.getExitStatus().getExitCode());
                jobDetails.put("exit_description", jobExecution.getExitStatus().getExitDescription());
                jobDetails.put("end_time", LocalDateTime.now().toString());
                
                // Log different audit events based on job outcome
                if (jobExecution.getStatus().isUnsuccessful()) {
                    auditService.logSecurityEvent("BATCH_JOB_FAILED", "HIGH", 
                                                 "Batch job failed: " + jobName, 
                                                 jobDetails);
                } else {
                    auditService.logTransactionEvent(username, "BATCH_JOB_COMPLETED", 
                                                    jobExecutionId.toString(), 
                                                    (double) duration, jobDetails);
                }
                
                // Log performance metrics for SLA monitoring
                if (duration > jobTimeoutSeconds * 1000) {
                    logger.warn("Batch job {} exceeded timeout threshold: {}ms > {}ms", 
                               jobName, duration, jobTimeoutSeconds * 1000);
                    
                    auditService.logSecurityEvent("BATCH_JOB_SLA_VIOLATION", "MEDIUM", 
                                                 "Batch job exceeded SLA timeout", 
                                                 jobDetails);
                }
            }
        };
    }

    /**
     * Configures a dedicated transaction manager for batch processing operations.
     * 
     * This transaction manager provides optimized transaction handling for batch
     * processing workloads with configurable isolation levels and performance
     * optimizations for high-volume data processing operations.
     * 
     * Features:
     * - Optimized transaction handling for batch processing
     * - Configurable isolation levels for data consistency
     * - Performance optimizations for high-volume operations
     * - Integration with HikariCP connection pooling
     * - Support for nested transactions and savepoints
     * 
     * @return PlatformTransactionManager configured for batch operations
     */
    @Bean
    public PlatformTransactionManager batchTransactionManager() {
        logger.info("Configuring batch-specific transaction manager");
        
        // Leverage the optimized transaction manager from DatabaseConfig
        PlatformTransactionManager transactionManager = databaseConfig.transactionManager(databaseConfig.entityManagerFactory(databaseConfig.dataSource()));
        
        logger.info("Batch transaction manager configured successfully");
        
        return transactionManager;
    }

    /**
     * Configures a RetryTemplate for transient failure handling in batch operations.
     * 
     * This RetryTemplate provides configurable retry logic for transient failures
     * in batch processing operations, supporting exponential backoff and maximum
     * retry attempts for enterprise-grade error handling.
     * 
     * Features:
     * - Configurable retry policies with exponential backoff
     * - Support for different exception types and retry strategies
     * - Integration with batch processing error handling
     * - Performance monitoring and metrics collection
     * - Audit logging of retry attempts and outcomes
     * 
     * @return RetryTemplate configured for batch processing retry logic
     */
    @Bean
    public RetryTemplate retryTemplate() {
        logger.info("Configuring RetryTemplate for batch processing error handling");
        
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure retry policy with maximum attempts
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(maxRetryAttempts);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Configure exponential backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialRetryInterval);
        backOffPolicy.setMaxInterval(maxRetryInterval);
        backOffPolicy.setMultiplier(retryMultiplier);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        logger.info("RetryTemplate configured - Max attempts: {}, Initial interval: {}ms, " +
                   "Max interval: {}ms, Multiplier: {}", 
                   maxRetryAttempts, initialRetryInterval, maxRetryInterval, retryMultiplier);
        
        return retryTemplate;
    }

    /**
     * Configures a SkipPolicy for failed record processing in batch operations.
     * 
     * This SkipPolicy enables failed record processing to be skipped while
     * maintaining job execution continuity and comprehensive error reporting.
     * Supports configurable skip limits and exception type handling.
     * 
     * Features:
     * - Configurable skip limits for failed record processing
     * - Exception type-based skip decision making
     * - Comprehensive error logging and reporting
     * - Integration with batch processing error handling
     * - Audit trail generation for skipped records
     * 
     * @return SkipPolicy configured for batch processing skip logic
     */
    @Bean
    public SkipPolicy skipPolicy() {
        logger.info("Configuring SkipPolicy for batch processing error handling");
        
        return new SkipPolicy() {
            @Override
            public boolean shouldSkip(Throwable exception, long skipCount) throws SkipLimitExceededException {
                // Check if skip limit has been exceeded
                if (skipCount >= maxSkipCount) {
                    logger.error("Skip limit exceeded: {} >= {}", skipCount, maxSkipCount);
                    
                    // Audit log skip limit exceeded
                    String username = getCurrentUsername();
                    Map<String, Object> skipDetails = new HashMap<>();
                    skipDetails.put("skip_count", skipCount);
                    skipDetails.put("max_skip_count", maxSkipCount);
                    skipDetails.put("exception_type", exception.getClass().getSimpleName());
                    skipDetails.put("exception_message", exception.getMessage());
                    
                    auditService.logSecurityEvent("BATCH_SKIP_LIMIT_EXCEEDED", "HIGH", 
                                                 "Batch job skip limit exceeded", 
                                                 skipDetails);
                    
                    return false;
                }
                
                // Define skippable exceptions for batch processing
                boolean shouldSkip = isSkippableException(exception);
                
                if (shouldSkip) {
                    logger.warn("Skipping record due to exception (count: {}): {}", 
                               skipCount + 1, exception.getMessage());
                    
                    // Audit log skipped record
                    String username = getCurrentUsername();
                    Map<String, Object> skipDetails = new HashMap<>();
                    skipDetails.put("skip_count", skipCount + 1);
                    skipDetails.put("exception_type", exception.getClass().getSimpleName());
                    skipDetails.put("exception_message", exception.getMessage());
                    
                    auditService.logTransactionEvent(username, "BATCH_RECORD_SKIPPED", 
                                                    "SKIP_" + (skipCount + 1), 
                                                    0.0, skipDetails);
                }
                
                return shouldSkip;
            }
        };
    }

    /**
     * Configures a TaskExecutor for asynchronous batch job execution.
     * 
     * This TaskExecutor provides thread pool management for concurrent batch
     * job execution with optimized settings for high-throughput processing
     * and resource utilization.
     * 
     * @return TaskExecutor configured for batch job execution
     */
    @Bean
    public TaskExecutor batchTaskExecutor() {
        logger.info("Configuring TaskExecutor for batch job execution");
        
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        
        logger.info("TaskExecutor configured - Core pool: {}, Max pool: {}, Queue capacity: {}", 
                   corePoolSize, maxPoolSize, queueCapacity);
        
        return executor;
    }

    /**
     * Determines if an exception is skippable during batch processing.
     * 
     * @param exception The exception to evaluate
     * @return true if the exception should be skipped, false otherwise
     */
    private boolean isSkippableException(Throwable exception) {
        // Define skippable exceptions for batch processing
        String exceptionClass = exception.getClass().getSimpleName();
        
        // Common skippable exceptions for batch processing
        return exceptionClass.contains("ValidationException") ||
               exceptionClass.contains("ConversionException") ||
               exceptionClass.contains("ParseException") ||
               exceptionClass.contains("DataIntegrityViolationException") ||
               exceptionClass.contains("ConstraintViolationException");
    }

    /**
     * Retrieves the current username from the security context.
     * 
     * @return Current username or "SYSTEM" if no authentication context
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated()) {
                return authentication.getName();
            }
        } catch (Exception e) {
            logger.debug("Unable to retrieve current username: {}", e.getMessage());
        }
        return "SYSTEM";
    }
}