package com.carddemo.service;

import net.sf.jasperreports.engine.JasperExportManager;
import org.springframework.stereotype.Service;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.SimplePdfReportConfiguration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import com.carddemo.transaction.Transaction;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ReportService - Spring Boot service providing JasperReports integration for PDF report generation
 * replacing COBOL report generation program CORPT00C.cbl
 * 
 * This service handles:
 * - Statement PDF generation with JasperReports templates
 * - Report parameter processing and validation
 * - Template management and compilation
 * - High-performance report generation for 100K+ statements
 * - Output formatting and file management
 * - Comprehensive error handling for report generation failures
 * 
 * Converted from COBOL program CORPT00C.cbl which provided:
 * - Monthly report generation (current month from 1st to last day)
 * - Yearly report generation (current year from Jan 1 to Dec 31)
 * - Custom date range report generation with validation
 * - JCL job submission for batch processing (now direct PDF generation)
 * - User confirmation workflow
 * - Comprehensive date validation
 * - Error handling and user feedback
 * 
 * Performance Requirements:
 * - Standard JasperReports generation within 30 seconds per Section 4.5.1
 * - PDF statement generation optimized for high-volume processing
 * - Batch report generation for 100K+ statements within SLA requirements
 * 
 * @author Blitzy Platform Migration Team
 * @version 1.0
 * @since 2024-01-01
 */
@Service
public class ReportService {

    private static final Logger logger = LoggerFactory.getLogger(ReportService.class);

    // Constants matching COBOL program structure
    private static final String PROGRAM_NAME = "CORPT00C"; // WS-PGMNAME from COBOL
    private static final String TRANSACTION_ID = "CR00";   // WS-TRANID from COBOL
    private static final String DATE_FORMAT = "YYYY-MM-DD"; // WS-DATE-FORMAT from COBOL
    
    // Report template paths
    private static final String MONTHLY_REPORT_TEMPLATE = "reports/monthly_transaction_report.jrxml";
    private static final String YEARLY_REPORT_TEMPLATE = "reports/yearly_transaction_report.jrxml";
    private static final String CUSTOM_REPORT_TEMPLATE = "reports/custom_transaction_report.jrxml";
    private static final String STATEMENT_TEMPLATE = "reports/statement_report.jrxml";
    
    // Performance optimization constants
    private static final int BATCH_SIZE = 1000;
    private static final int THREAD_POOL_SIZE = 4;
    private static final long REPORT_TIMEOUT_SECONDS = 30;
    
    // Error messages matching COBOL validation patterns
    private static final String ERROR_START_DATE_MONTH_EMPTY = "Start Date - Month can NOT be empty...";
    private static final String ERROR_START_DATE_DAY_EMPTY = "Start Date - Day can NOT be empty...";
    private static final String ERROR_START_DATE_YEAR_EMPTY = "Start Date - Year can NOT be empty...";
    private static final String ERROR_END_DATE_MONTH_EMPTY = "End Date - Month can NOT be empty...";
    private static final String ERROR_END_DATE_DAY_EMPTY = "End Date - Day can NOT be empty...";
    private static final String ERROR_END_DATE_YEAR_EMPTY = "End Date - Year can NOT be empty...";
    private static final String ERROR_START_DATE_INVALID_MONTH = "Start Date - Not a valid Month...";
    private static final String ERROR_START_DATE_INVALID_DAY = "Start Date - Not a valid Day...";
    private static final String ERROR_START_DATE_INVALID_YEAR = "Start Date - Not a valid Year...";
    private static final String ERROR_END_DATE_INVALID_MONTH = "End Date - Not a valid Month...";
    private static final String ERROR_END_DATE_INVALID_DAY = "End Date - Not a valid Day...";
    private static final String ERROR_END_DATE_INVALID_YEAR = "End Date - Not a valid Year...";
    private static final String ERROR_START_DATE_INVALID = "Start Date - Not a valid date...";
    private static final String ERROR_END_DATE_INVALID = "End Date - Not a valid date...";
    
    @Autowired
    private DataSource dataSource;
    
    private final ExecutorService executorService;
    private final Map<String, JasperReport> compiledReports;
    
    /**
     * Constructor initializing thread pool and compiled reports cache
     */
    public ReportService() {
        this.executorService = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.compiledReports = new HashMap<>();
    }

    /**
     * Generate statement report with specified parameters
     * Replaces COBOL PROCESS-ENTER-KEY paragraph logic
     * 
     * @param reportType Type of report: "Monthly", "Yearly", or "Custom"
     * @param startDate Start date for custom reports (YYYY-MM-DD format)
     * @param endDate End date for custom reports (YYYY-MM-DD format)
     * @param confirmed User confirmation (Y/N)
     * @return ReportGenerationResult containing PDF bytes or error message
     */
    public ReportGenerationResult generateStatementReport(String reportType, String startDate, String endDate, String confirmed) {
        logger.info("Starting statement report generation: type={}, startDate={}, endDate={}, confirmed={}", 
                   reportType, startDate, endDate, confirmed);
        
        try {
            // Validate report type selection - matching COBOL EVALUATE TRUE logic
            if (!StringUtils.hasText(reportType)) {
                return ReportGenerationResult.error("Select a report type to print report...");
            }
            
            String actualStartDate = null;
            String actualEndDate = null;
            String reportName = null;
            
            // Process report type - matching COBOL EVALUATE TRUE structure
            switch (reportType.toLowerCase()) {
                case "monthly":
                    reportName = "Monthly";
                    // Calculate monthly date range - matching COBOL monthly logic
                    LocalDate now = LocalDate.now();
                    LocalDate monthStart = now.withDayOfMonth(1);
                    LocalDate monthEnd = now.withDayOfMonth(now.lengthOfMonth());
                    actualStartDate = monthStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    actualEndDate = monthEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    break;
                    
                case "yearly":
                    reportName = "Yearly";
                    // Calculate yearly date range - matching COBOL yearly logic
                    LocalDate yearNow = LocalDate.now();
                    LocalDate yearStart = LocalDate.of(yearNow.getYear(), 1, 1);
                    LocalDate yearEnd = LocalDate.of(yearNow.getYear(), 12, 31);
                    actualStartDate = yearStart.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    actualEndDate = yearEnd.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    break;
                    
                case "custom":
                    reportName = "Custom";
                    // Validate custom date parameters - matching COBOL custom validation
                    ReportParameterValidation validation = validateReportParameters(startDate, endDate);
                    if (!validation.isValid()) {
                        return ReportGenerationResult.error(validation.getErrorMessage());
                    }
                    actualStartDate = startDate;
                    actualEndDate = endDate;
                    break;
                    
                default:
                    return ReportGenerationResult.error("Select a report type to print report...");
            }
            
            // Confirm report generation - matching COBOL SUBMIT-JOB-TO-INTRDR logic
            if (!StringUtils.hasText(confirmed)) {
                String confirmationMessage = String.format("Please confirm to print the %s report...", reportName);
                return ReportGenerationResult.confirmation(confirmationMessage);
            }
            
            // Process confirmation - matching COBOL confirmation validation
            if (!isConfirmed(confirmed)) {
                if ("N".equalsIgnoreCase(confirmed) || "n".equalsIgnoreCase(confirmed)) {
                    return ReportGenerationResult.error("Report generation cancelled by user");
                } else {
                    return ReportGenerationResult.error(String.format("\"%s\" is not a valid value to confirm...", confirmed));
                }
            }
            
            // Generate PDF report
            byte[] pdfBytes = generatePdfReport(reportName, actualStartDate, actualEndDate);
            
            String successMessage = String.format("%s report submitted for printing ...", reportName);
            logger.info("Report generation completed successfully: {}", successMessage);
            
            return ReportGenerationResult.success(pdfBytes, successMessage);
            
        } catch (Exception e) {
            logger.error("Error generating statement report", e);
            return ReportGenerationResult.error("Unable to generate report: " + e.getMessage());
        }
    }

    public byte[] generateStatementReport(List<Transaction> transactions, Map<String, Object> reportParameters) {
    	return null;
    }

    /**
     * Generate PDF report with specified parameters
     * Implements JasperReports PDF generation with performance optimization
     * 
     * @param reportName Name of the report being generated
     * @param startDate Start date for report data (YYYY-MM-DD format)
     * @param endDate End date for report data (YYYY-MM-DD format)
     * @return PDF bytes for the generated report
     */
    public byte[] generatePdfReport(String reportName, String startDate, String endDate) {
        logger.info("Generating PDF report: name={}, startDate={}, endDate={}", reportName, startDate, endDate);
        
        try {
            // Determine template based on report type
            String templatePath = getReportTemplatePath(reportName);
            
            // Compile report template
            JasperReport jasperReport = compileReportTemplate(templatePath);
            
            // Prepare report parameters
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("REPORT_NAME", reportName);
            parameters.put("START_DATE", startDate);
            parameters.put("END_DATE", endDate);
            parameters.put("GENERATED_DATE", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            parameters.put("PROGRAM_NAME", PROGRAM_NAME);
            parameters.put("TRANSACTION_ID", TRANSACTION_ID);
            
            // Fill report with data
            JasperPrint jasperPrint = fillReportWithData(jasperReport, parameters);
            
            // Export to PDF
            return exportReportToPdf(jasperPrint);
            
        } catch (Exception e) {
            logger.error("Error generating PDF report", e);
            throw new RuntimeException("Failed to generate PDF report: " + e.getMessage(), e);
        }
    }

    /**
     * Compile JasperReports template with caching for performance
     * 
     * @param templatePath Path to the JRXML template file
     * @return Compiled JasperReport object
     */
    public JasperReport compileReportTemplate(String templatePath) {
        logger.debug("Compiling report template: {}", templatePath);
        
        try {
            // Check cache first for performance optimization
            if (compiledReports.containsKey(templatePath)) {
                logger.debug("Using cached compiled report for template: {}", templatePath);
                return compiledReports.get(templatePath);
            }
            
            // Load template from classpath
            Resource resource = new ClassPathResource(templatePath);
            
            if (!resource.exists()) {
                throw new RuntimeException("Report template not found: " + templatePath);
            }
            
            // Compile template
            try (InputStream inputStream = resource.getInputStream()) {
                JasperReport jasperReport = JasperCompileManager.compileReport(inputStream);
                
                // Cache compiled report for performance
                compiledReports.put(templatePath, jasperReport);
                
                logger.debug("Successfully compiled and cached report template: {}", templatePath);
                return jasperReport;
            }
            
        } catch (Exception e) {
            logger.error("Error compiling report template: {}", templatePath, e);
            throw new RuntimeException("Failed to compile report template: " + e.getMessage(), e);
        }
    }

    /**
     * Fill report with data using database connection
     * 
     * @param jasperReport Compiled JasperReport object
     * @param parameters Report parameters map
     * @return Filled JasperPrint object ready for export
     */
    public JasperPrint fillReportWithData(JasperReport jasperReport, Map<String, Object> parameters) {
        logger.debug("Filling report with data, parameters: {}", parameters);
        
        try (Connection connection = dataSource.getConnection()) {
            // Fill report using database connection
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, connection);
            
            logger.debug("Successfully filled report with data");
            return jasperPrint;
            
        } catch (Exception e) {
            logger.error("Error filling report with data", e);
            throw new RuntimeException("Failed to fill report with data: " + e.getMessage(), e);
        }
    }

    /**
     * Export JasperPrint to PDF format with optimized settings
     * 
     * @param jasperPrint Filled JasperPrint object
     * @return PDF bytes
     */
    public byte[] exportReportToPdf(JasperPrint jasperPrint) {
        logger.debug("Exporting report to PDF");
        
        try {
            // Configure PDF export for optimal performance
            configureReportExport();
            
            // Export to PDF using JasperExportManager
            return JasperExportManager.exportReportToPdf(jasperPrint);
            
        } catch (Exception e) {
            logger.error("Error exporting report to PDF", e);
            throw new RuntimeException("Failed to export report to PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Validate report parameters with comprehensive COBOL-style validation
     * Replicates COBOL custom date validation logic from CORPT00C
     * 
     * @param startDate Start date string (YYYY-MM-DD format)
     * @param endDate End date string (YYYY-MM-DD format)
     * @return ReportParameterValidation object with validation results
     */
    public ReportParameterValidation validateReportParameters(String startDate, String endDate) {
        logger.debug("Validating report parameters: startDate={}, endDate={}", startDate, endDate);
        
        try {
            // Validate start date components - matching COBOL validation structure
            if (!StringUtils.hasText(startDate)) {
                return ReportParameterValidation.invalid(ERROR_START_DATE_MONTH_EMPTY);
            }
            
            if (!StringUtils.hasText(endDate)) {
                return ReportParameterValidation.invalid(ERROR_END_DATE_MONTH_EMPTY);
            }
            
            // Parse and validate start date
            LocalDate parsedStartDate = parseAndValidateDate(startDate, true);
            if (parsedStartDate == null) {
                return ReportParameterValidation.invalid(ERROR_START_DATE_INVALID);
            }
            
            // Parse and validate end date
            LocalDate parsedEndDate = parseAndValidateDate(endDate, false);
            if (parsedEndDate == null) {
                return ReportParameterValidation.invalid(ERROR_END_DATE_INVALID);
            }
            
            // Validate date range
            if (parsedStartDate.isAfter(parsedEndDate)) {
                return ReportParameterValidation.invalid("Start date cannot be after end date");
            }
            
            // Validate date range is reasonable (not more than 1 year)
            if (parsedStartDate.plusYears(1).isBefore(parsedEndDate)) {
                return ReportParameterValidation.invalid("Date range cannot exceed 1 year");
            }
            
            logger.debug("Report parameter validation successful");
            return ReportParameterValidation.valid();
            
        } catch (Exception e) {
            logger.error("Error validating report parameters", e);
            return ReportParameterValidation.invalid("Error validating parameters: " + e.getMessage());
        }
    }

    /**
     * Get report template path based on report type
     * 
     * @param reportName Name of the report
     * @return Path to the report template
     */
    public String getReportTemplate(String reportName) {
        return getReportTemplatePath(reportName);
    }

    /**
     * Configure report export settings for optimal PDF generation
     * Sets up JasperReports export configuration for performance
     */
    public void configureReportExport() {
        logger.debug("Configuring report export settings");
        
        // Set JVM properties for optimal PDF generation
        System.setProperty("net.sf.jasperreports.awt.ignore.missing.font", "true");
        System.setProperty("net.sf.jasperreports.default.pdf.font.name", "DejaVu Sans");
        System.setProperty("net.sf.jasperreports.default.pdf.encoding", "UTF-8");
        System.setProperty("net.sf.jasperreports.default.pdf.embedded", "true");
        
        logger.debug("Report export configuration completed");
    }

    /**
     * Generate batch reports for high-volume processing
     * Optimized for 100K+ statements with parallel processing
     * 
     * @param reportRequests List of report generation requests
     * @return List of futures for batch processing results
     */
    public List<Future<ReportGenerationResult>> generateBatchReports(List<BatchReportRequest> reportRequests) {
        logger.info("Starting batch report generation for {} requests", reportRequests.size());
        
        List<Future<ReportGenerationResult>> futures = new ArrayList<>();
        
        try {
            // Process requests in batches for optimal performance
            for (int i = 0; i < reportRequests.size(); i += BATCH_SIZE) {
                int endIndex = Math.min(i + BATCH_SIZE, reportRequests.size());
                List<BatchReportRequest> batch = reportRequests.subList(i, endIndex);
                
                // Submit batch processing tasks
                for (BatchReportRequest request : batch) {
                    Future<ReportGenerationResult> future = executorService.submit(() -> {
                        try {
                            return generateStatementReport(
                                request.getReportType(),
                                request.getStartDate(),
                                request.getEndDate(),
                                "Y" // Auto-confirm for batch processing
                            );
                        } catch (Exception e) {
                            logger.error("Error in batch report generation", e);
                            return ReportGenerationResult.error("Batch processing error: " + e.getMessage());
                        }
                    });
                    
                    futures.add(future);
                }
            }
            
            logger.info("Submitted {} batch report generation tasks", futures.size());
            return futures;
            
        } catch (Exception e) {
            logger.error("Error in batch report generation", e);
            throw new RuntimeException("Failed to generate batch reports: " + e.getMessage(), e);
        }
    }

    /**
     * Optimize report generation performance
     * Implements performance tuning for high-volume report generation
     */
    public void optimizeReportGeneration() {
        logger.info("Optimizing report generation performance");
        
        try {
            // Pre-compile frequently used templates
            compileReportTemplate(MONTHLY_REPORT_TEMPLATE);
            compileReportTemplate(YEARLY_REPORT_TEMPLATE);
            compileReportTemplate(CUSTOM_REPORT_TEMPLATE);
            compileReportTemplate(STATEMENT_TEMPLATE);
            
            // Configure JasperReports performance settings
            configureReportExport();
            
            // Warm up database connections
            try (Connection connection = dataSource.getConnection()) {
                logger.debug("Database connection warmed up");
            }
            
            logger.info("Report generation optimization completed");
            
        } catch (Exception e) {
            logger.error("Error optimizing report generation", e);
            throw new RuntimeException("Failed to optimize report generation: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    /**
     * Get report template path based on report name
     */
    private String getReportTemplatePath(String reportName) {
        switch (reportName.toLowerCase()) {
            case "monthly":
                return MONTHLY_REPORT_TEMPLATE;
            case "yearly":
                return YEARLY_REPORT_TEMPLATE;
            case "custom":
                return CUSTOM_REPORT_TEMPLATE;
            default:
                return STATEMENT_TEMPLATE;
        }
    }

    /**
     * Parse and validate date string with COBOL-style validation
     * Replicates COBOL date validation logic
     */
    private LocalDate parseAndValidateDate(String dateString, boolean isStartDate) {
        try {
            // Parse date in YYYY-MM-DD format
            LocalDate date = LocalDate.parse(dateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            
            // Validate date components like COBOL
            int month = date.getMonthValue();
            int day = date.getDayOfMonth();
            int year = date.getYear();
            
            // Validate month range
            if (month < 1 || month > 12) {
                return null;
            }
            
            // Validate day range
            if (day < 1 || day > 31) {
                return null;
            }
            
            // Validate year range (reasonable range)
            if (year < 1900 || year > 2100) {
                return null;
            }
            
            return date;
            
        } catch (DateTimeParseException e) {
            logger.debug("Date parsing failed for: {}", dateString);
            return null;
        }
    }

    /**
     * Check if user confirmation is valid
     * Matches COBOL confirmation validation logic
     */
    private boolean isConfirmed(String confirmed) {
        return "Y".equalsIgnoreCase(confirmed) || "y".equalsIgnoreCase(confirmed);
    }

    /**
     * Shutdown executor service when service is destroyed
     */
    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    // Inner classes for structured responses

    /**
     * Report generation result wrapper
     */
    public static class ReportGenerationResult {
        private final boolean success;
        private final boolean needsConfirmation;
        private final String message;
        private final byte[] pdfBytes;
        private final String errorMessage;

        private ReportGenerationResult(boolean success, boolean needsConfirmation, String message, byte[] pdfBytes, String errorMessage) {
            this.success = success;
            this.needsConfirmation = needsConfirmation;
            this.message = message;
            this.pdfBytes = pdfBytes;
            this.errorMessage = errorMessage;
        }

        public static ReportGenerationResult success(byte[] pdfBytes, String message) {
            return new ReportGenerationResult(true, false, message, pdfBytes, null);
        }

        public static ReportGenerationResult error(String errorMessage) {
            return new ReportGenerationResult(false, false, null, null, errorMessage);
        }

        public static ReportGenerationResult confirmation(String message) {
            return new ReportGenerationResult(false, true, message, null, null);
        }

        public boolean isSuccess() { return success; }
        public boolean needsConfirmation() { return needsConfirmation; }
        public String getMessage() { return message; }
        public byte[] getPdfBytes() { return pdfBytes; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Report parameter validation result
     */
    public static class ReportParameterValidation {
        private final boolean valid;
        private final String errorMessage;

        private ReportParameterValidation(boolean valid, String errorMessage) {
            this.valid = valid;
            this.errorMessage = errorMessage;
        }

        public static ReportParameterValidation valid() {
            return new ReportParameterValidation(true, null);
        }

        public static ReportParameterValidation invalid(String errorMessage) {
            return new ReportParameterValidation(false, errorMessage);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
    }

    /**
     * Batch report request structure
     */
    public static class BatchReportRequest {
        private final String reportType;
        private final String startDate;
        private final String endDate;
        private final String accountId;
        private final String customerId;

        public BatchReportRequest(String reportType, String startDate, String endDate, String accountId, String customerId) {
            this.reportType = reportType;
            this.startDate = startDate;
            this.endDate = endDate;
            this.accountId = accountId;
            this.customerId = customerId;
        }

        public String getReportType() { return reportType; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }
        public String getAccountId() { return accountId; }
        public String getCustomerId() { return customerId; }
    }
}