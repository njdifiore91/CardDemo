package com.carddemo.transaction;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carddemo.entity.Transaction;
import com.carddemo.session.SessionManagementService;
import com.carddemo.session.SessionManagementService.NavigationContext;
import com.carddemo.session.SessionManagementService.TransactionContext;

/**
 * REST controller providing transaction management endpoints that replicate CICS transaction codes
 * with session management, exception handling, and request/response processing matching BMS map layouts.
 * 
 * This controller transforms the following COBOL programs to REST endpoints:
 * - COTRN00C: Transaction listing with pagination and selection (CT00)
 * - COTRN01C: Transaction detail viewing and validation (CT01)
 * - COTRN02C: Transaction creation with comprehensive validation (CT02)
 * 
 * Key Features:
 * - REST API endpoints mapping to CICS transaction codes
 * - Session management replacing COMMAREA pseudo-conversational state
 * - Exception handling replicating HANDLE ABEND patterns
 * - Request/response DTOs matching BMS map layouts
 * - Pagination support with 10 records per page (matching COBOL screen layout)
 * - Comprehensive field validation matching COBOL 88-level conditions
 * - Error handling with CICS response code mapping to HTTP status codes
 * 
 * URL Mappings:
 * - GET /api/transactions - List transactions with pagination (CT00)
 * - GET /api/transactions/{id} - Get transaction by ID (CT01)
 * - POST /api/transactions - Create new transaction (CT02)
 * - GET /api/transactions/card/{cardNumber} - Get transactions by card number
 * - GET /api/transactions/account/{accountId} - Get transactions by account ID
 * - GET /api/transactions/search - Search transactions with date range
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/api/transactions")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class TransactionController {

    private static final Logger logger = LoggerFactory.getLogger(TransactionController.class);

    // Constants for transaction processing (derived from COBOL programs)
    private static final int DEFAULT_PAGE_SIZE = 10; // COTRN00C pagination size
    private static final int MAX_PAGE_SIZE = 100; // Maximum page size for safety
    private static final String DEFAULT_SORT_FIELD = "transactionTimestamp";
    private static final String DEFAULT_SORT_DIRECTION = "DESC";
    
    // CICS response code equivalents
    private static final String CICS_RESPONSE_NORMAL = "NORMAL";
    private static final String CICS_RESPONSE_NOTFND = "NOTFND";
    private static final String CICS_RESPONSE_DUPKEY = "DUPKEY";
    private static final String CICS_RESPONSE_INVALID = "INVALID";
    
    // Error message constants matching COBOL programs
    private static final String MSG_TRANSACTION_NOT_FOUND = "Transaction ID NOT found...";
    private static final String MSG_INVALID_TRANSACTION_ID = "Transaction ID must be exactly 16 characters";
    private static final String MSG_INVALID_CARD_NUMBER = "Card number must be exactly 16 digits";
    private static final String MSG_INVALID_ACCOUNT_ID = "Account ID must be exactly 11 digits";
    private static final String MSG_INVALID_KEY = "Invalid selection. Valid value is S";
    private static final String MSG_TRANSACTION_ADDED = "Transaction added successfully. Your Tran ID is ";
    private static final String MSG_UNABLE_TO_LOOKUP = "Unable to lookup transaction...";
    private static final String MSG_ALREADY_AT_TOP = "You are already at the top of the page...";
    private static final String MSG_ALREADY_AT_BOTTOM = "You are already at the bottom of the page...";

    // Service dependencies
    private final TransactionService transactionService;
    private final SessionManagementService sessionManagementService;

    /**
     * Constructor for dependency injection
     * 
     * @param transactionService Primary transaction service for business logic
     * @param sessionManagementService Session management service for COMMAREA replacement
     */
    @Autowired
    public TransactionController(TransactionService transactionService,
                               SessionManagementService sessionManagementService) {
        this.transactionService = transactionService;
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Lists transactions with pagination support
     * 
     * Replicates COTRN00C functionality with 10-record pagination and PF7/PF8 navigation.
     * Supports transaction ID filtering and page-based navigation.
     * 
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10, max 100)
     * @param sortBy Field to sort by (default transactionTimestamp)
     * @param sortDirection Sort direction (default DESC)
     * @param transactionId Optional transaction ID to start from
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated transaction list
     */
    @GetMapping
    public ResponseEntity<TransactionListResponse> listTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = DEFAULT_SORT_FIELD) String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
            @RequestParam(required = false) String transactionId,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transaction list request - page: {}, size: {}, transactionId: {}", 
                       page, size, transactionId);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_LIST");
            
            // Validate pagination parameters
            if (page < 0) {
                page = 0;
            }
            if (size <= 0 || size > MAX_PAGE_SIZE) {
                size = DEFAULT_PAGE_SIZE;
            }
            
            // Get paginated transactions
            Page<Transaction> transactionPage = transactionService.listTransactions(
                page, size, sortBy, sortDirection);
            
            // Update transaction context with pagination info
            updateTransactionContext(request, transactionPage);
            
            // Build response matching BMS map layout (COTRN0A)
            TransactionListResponse response = new TransactionListResponse();
            response.setTransactions(transactionPage.getContent());
            response.setPageNumber(transactionPage.getNumber());
            response.setPageSize(transactionPage.getSize());
            response.setTotalElements(transactionPage.getTotalElements());
            response.setTotalPages(transactionPage.getTotalPages());
            response.setFirst(transactionPage.isFirst());
            response.setLast(transactionPage.isLast());
            response.setHasNext(!transactionPage.isLast());
            response.setHasPrevious(!transactionPage.isFirst());
            
            // Set navigation messages
            if (transactionPage.isFirst()) {
                response.setMessage(MSG_ALREADY_AT_TOP);
            } else if (transactionPage.isLast()) {
                response.setMessage(MSG_ALREADY_AT_BOTTOM);
            } else {
                response.setMessage("Page " + (page + 1) + " of " + transactionPage.getTotalPages());
            }
            
            response.setStatus(CICS_RESPONSE_NORMAL);
            
            logger.info("Transaction list retrieved successfully - {} transactions on page {}", 
                       transactionPage.getNumberOfElements(), page);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error processing transaction list request: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error retrieving transaction list");
        }
    }

    /**
     * Retrieves a specific transaction by ID
     * 
     * Replicates COTRN01C functionality with comprehensive field display
     * and validation error handling.
     * 
     * @param transactionId 16-character transaction identifier
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with transaction details
     */
    @GetMapping("/{transactionId}")
    public ResponseEntity<TransactionDetailResponse> getTransactionById(
            @PathVariable @NotNull @Size(min = 16, max = 16, message = MSG_INVALID_TRANSACTION_ID) String transactionId,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transaction detail request for ID: {}", transactionId);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT01", "TRANSACTION_VIEW");
            
            // Get transaction details
            Transaction transaction = transactionService.getTransactionById(transactionId);
            
            // Update transaction context with current transaction
            updateTransactionContextWithTransaction(request, transaction);
            
            // Build response matching BMS map layout (COTRN1A)
            TransactionDetailResponse response = new TransactionDetailResponse();
            response.setTransaction(transaction);
            response.setStatus(CICS_RESPONSE_NORMAL);
            response.setMessage("Transaction details retrieved successfully");
            
            logger.info("Transaction details retrieved successfully for ID: {}", transactionId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving transaction details for ID {}: {}", transactionId, e.getMessage(), e);
            return handleTransactionDetailException(e, transactionId);
        }
    }

    /**
     * Creates a new transaction with comprehensive validation
     * 
     * Replicates COTRN02C functionality with validation and cross-reference lookup.
     * Includes confirmation processing and duplicate detection.
     * 
     * @param transactionRequest Transaction creation request
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with creation result
     */
    @PostMapping
    public ResponseEntity<TransactionCreateResponse> createTransaction(
            @Valid @RequestBody TransactionCreateRequest transactionRequest,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transaction creation request for card: {}", 
                       maskCardNumber(transactionRequest.getCardNumber()));
            
            // Update navigation context in session
            updateNavigationContext(request, "CT02", "TRANSACTION_CREATE");
            
            // Validate transaction data
            validateTransactionData(transactionRequest);
            
            // Build transaction entity from request
            Transaction transaction = buildTransactionFromRequest(transactionRequest);
            
            // Create transaction through service
            Transaction createdTransaction = transactionService.createTransaction(transaction);
            
            // Update transaction context with created transaction
            updateTransactionContextWithTransaction(request, createdTransaction);
            
            // Build response matching BMS map layout (COTRN2A)
            TransactionCreateResponse response = new TransactionCreateResponse();
            response.setTransaction(createdTransaction);
            response.setStatus(CICS_RESPONSE_NORMAL);
            response.setMessage(MSG_TRANSACTION_ADDED + createdTransaction.getTransactionId() + ".");
            response.setTransactionId(createdTransaction.getTransactionId());
            
            logger.info("Transaction created successfully with ID: {}", createdTransaction.getTransactionId());
            
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
            
        } catch (Exception e) {
            logger.error("Error creating transaction: {}", e.getMessage(), e);
            return handleTransactionCreateException(e, transactionRequest);
        }
    }

    /**
     * Retrieves transactions by card number with pagination
     * 
     * @param cardNumber 16-digit card number
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10)
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated transaction list
     */
    @GetMapping("/card/{cardNumber}")
    public ResponseEntity<TransactionListResponse> getTransactionsByCardNumber(
            @PathVariable @NotNull @Size(min = 16, max = 16, message = MSG_INVALID_CARD_NUMBER) String cardNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transactions by card number request for card: {}", 
                       maskCardNumber(cardNumber));
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_BY_CARD");
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Get transactions by card number
            Page<Transaction> transactionPage = transactionService.findByCardNumber(cardNumber, page, size);
            
            // Build response
            TransactionListResponse response = buildTransactionListResponse(transactionPage);
            
            logger.info("Retrieved {} transactions for card: {}", 
                       transactionPage.getNumberOfElements(), maskCardNumber(cardNumber));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving transactions by card number: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error retrieving transactions by card number");
        }
    }

    /**
     * Retrieves transactions by account ID with pagination
     * 
     * @param accountId 11-digit account identifier
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10)
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated transaction list
     */
    @GetMapping("/account/{accountId}")
    public ResponseEntity<TransactionListResponse> getTransactionsByAccountId(
            @PathVariable @NotNull @Size(min = 11, max = 11, message = MSG_INVALID_ACCOUNT_ID) String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transactions by account ID request for account: {}", accountId);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_BY_ACCOUNT");
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Get transactions by account ID
            Page<Transaction> transactionPage = transactionService.findByAccountId(accountId, page, size);
            
            // Build response
            TransactionListResponse response = buildTransactionListResponse(transactionPage);
            
            logger.info("Retrieved {} transactions for account: {}", 
                       transactionPage.getNumberOfElements(), accountId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving transactions by account ID: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error retrieving transactions by account ID");
        }
    }

    /**
     * Searches transactions within a date range
     * 
     * @param startDate Start date in YYYY-MM-DD format
     * @param endDate End date in YYYY-MM-DD format
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10)
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated transaction list
     */
    @GetMapping("/search")
    public ResponseEntity<TransactionListResponse> getTransactionsByDateRange(
            @RequestParam @NotNull String startDate,
            @RequestParam @NotNull String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transactions by date range request: {} to {}", startDate, endDate);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_BY_DATE_RANGE");
            
            // Parse dates
            LocalDateTime startDateTime = parseDate(startDate);
            LocalDateTime endDateTime = parseDate(endDate);
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Get transactions by date range
            Page<Transaction> transactionPage = transactionService.getTransactionsByDateRange(
                startDateTime, endDateTime, page, size);
            
            // Build response
            TransactionListResponse response = buildTransactionListResponse(transactionPage);
            
            logger.info("Retrieved {} transactions for date range: {} to {}", 
                       transactionPage.getNumberOfElements(), startDate, endDate);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving transactions by date range: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error retrieving transactions by date range");
        }
    }

    /**
     * Retrieves transaction history for an account
     * 
     * @param accountId 11-digit account identifier
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10)
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated transaction history
     */
    @GetMapping("/history/{accountId}")
    public ResponseEntity<TransactionListResponse> getTransactionHistory(
            @PathVariable @NotNull @Size(min = 11, max = 11, message = MSG_INVALID_ACCOUNT_ID) String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transaction history request for account: {}", accountId);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_HISTORY");
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Get transaction history
            Page<Transaction> transactionPage = transactionService.getTransactionHistory(accountId, page, size);
            
            // Build response
            TransactionListResponse response = buildTransactionListResponse(transactionPage);
            
            logger.info("Retrieved {} transactions for account history: {}", 
                       transactionPage.getNumberOfElements(), accountId);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving transaction history: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error retrieving transaction history");
        }
    }

    /**
     * Calculates current account balance
     * 
     * @param accountId 11-digit account identifier
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with calculated balance
     */
    @GetMapping("/balance/{accountId}")
    public ResponseEntity<BalanceResponse> calculateTransactionBalance(
            @PathVariable @NotNull @Size(min = 11, max = 11, message = MSG_INVALID_ACCOUNT_ID) String accountId,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing balance calculation request for account: {}", accountId);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "BALANCE_CALCULATION");
            
            // Calculate balance
            BigDecimal balance = transactionService.calculateBalance(accountId);
            
            // Build response
            BalanceResponse response = new BalanceResponse();
            response.setAccountId(accountId);
            response.setBalance(balance);
            response.setStatus(CICS_RESPONSE_NORMAL);
            response.setMessage("Balance calculated successfully");
            
            logger.info("Balance calculated successfully for account: {} - Balance: {}", accountId, balance);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error calculating balance for account {}: {}", accountId, e.getMessage(), e);
            return handleBalanceException(e, accountId);
        }
    }

    /**
     * Searches transactions with advanced filtering criteria
     * 
     * @param cardNumber Optional card number filter
     * @param accountId Optional account ID filter
     * @param minAmount Optional minimum amount filter
     * @param maxAmount Optional maximum amount filter
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10)
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated search results
     */
    @GetMapping("/search/advanced")
    public ResponseEntity<TransactionListResponse> searchTransactions(
            @RequestParam(required = false) String cardNumber,
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing advanced transaction search request");
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_SEARCH");
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Perform search based on criteria
            Page<Transaction> transactionPage;
            
            if (cardNumber != null && !cardNumber.trim().isEmpty()) {
                validateCardNumber(cardNumber);
                transactionPage = transactionService.findByCardNumber(cardNumber, page, size);
            } else if (accountId != null && !accountId.trim().isEmpty()) {
                validateAccountId(accountId);
                transactionPage = transactionService.findByAccountId(accountId, page, size);
            } else if (minAmount != null && maxAmount != null) {
                List<Transaction> transactions = transactionService.getTransactionsByAmountRange(minAmount, maxAmount);
                // Convert to page (simplified for this example)
                transactionPage = transactionService.listTransactions(page, size, DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION);
            } else {
                transactionPage = transactionService.listTransactions(page, size, DEFAULT_SORT_FIELD, DEFAULT_SORT_DIRECTION);
            }
            
            // Build response
            TransactionListResponse response = buildTransactionListResponse(transactionPage);
            
            logger.info("Advanced search completed - {} transactions found", transactionPage.getNumberOfElements());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error performing advanced transaction search: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error performing advanced transaction search");
        }
    }

    /**
     * Retrieves paginated transactions with advanced options
     * 
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10)
     * @param sortBy Field to sort by (default transactionTimestamp)
     * @param sortDirection Sort direction (default DESC)
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with paginated transaction list
     */
    @GetMapping("/paginated")
    public ResponseEntity<TransactionListResponse> getTransactionsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = DEFAULT_SORT_FIELD) String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing paginated transaction request - page: {}, size: {}", page, size);
            
            // Update navigation context in session
            updateNavigationContext(request, "CT00", "TRANSACTION_PAGINATED");
            
            // Validate pagination parameters
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Get paginated transactions
            Page<Transaction> transactionPage = transactionService.listTransactions(page, size, sortBy, sortDirection);
            
            // Build response
            TransactionListResponse response = buildTransactionListResponse(transactionPage);
            
            logger.info("Paginated transactions retrieved - {} transactions on page {}", 
                       transactionPage.getNumberOfElements(), page);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error retrieving paginated transactions: {}", e.getMessage(), e);
            return handleTransactionException(e, "Error retrieving paginated transactions");
        }
    }

    /**
     * Validates transaction data comprehensively
     * 
     * Replicates COBOL validation logic from COTRN02C program
     * 
     * @param transactionRequest Transaction data to validate
     * @param request HTTP servlet request for session management
     * @return ResponseEntity with validation results
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validateTransactionData(
            @Valid @RequestBody TransactionCreateRequest transactionRequest,
            HttpServletRequest request) {
        
        try {
            logger.info("Processing transaction validation request");
            
            // Update navigation context in session
            updateNavigationContext(request, "CT02", "TRANSACTION_VALIDATE");
            
            // Validate transaction data
            validateTransactionData(transactionRequest);
            
            // Build validation response
            ValidationResponse response = new ValidationResponse();
            response.setValid(true);
            response.setStatus(CICS_RESPONSE_NORMAL);
            response.setMessage("Transaction data validation passed");
            
            logger.info("Transaction data validation completed successfully");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error validating transaction data: {}", e.getMessage(), e);
            return handleValidationException(e, transactionRequest);
        }
    }

    // =========================================================================================
    // EXCEPTION HANDLERS (REPLICATING HANDLE ABEND PATTERNS)
    // =========================================================================================

    /**
     * Handles transaction-related exceptions
     * 
     * Replicates COBOL HANDLE ABEND patterns with standardized error responses
     * 
     * @param e Exception to handle
     * @param context Error context description
     * @return ResponseEntity with error response
     */
    public ResponseEntity<TransactionListResponse> handleTransactionException(Exception e, String context) {
        TransactionListResponse response = new TransactionListResponse();
        response.setStatus(CICS_RESPONSE_INVALID);
        response.setMessage(context + ": " + e.getMessage());
        
        if (e.getMessage().contains("NOT found")) {
            response.setStatus(CICS_RESPONSE_NOTFND);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if (e.getMessage().contains("Invalid")) {
            response.setStatus(CICS_RESPONSE_INVALID);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handles transaction detail exceptions
     * 
     * @param e Exception to handle
     * @param transactionId Transaction ID that caused the error
     * @return ResponseEntity with error response
     */
    public ResponseEntity<TransactionDetailResponse> handleTransactionDetailException(Exception e, String transactionId) {
        TransactionDetailResponse response = new TransactionDetailResponse();
        response.setStatus(CICS_RESPONSE_INVALID);
        response.setMessage("Error retrieving transaction " + transactionId + ": " + e.getMessage());
        
        if (e.getMessage().contains("NOT found")) {
            response.setStatus(CICS_RESPONSE_NOTFND);
            response.setMessage(MSG_TRANSACTION_NOT_FOUND);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if (e.getMessage().contains("Invalid")) {
            response.setStatus(CICS_RESPONSE_INVALID);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handles transaction creation exceptions
     * 
     * @param e Exception to handle
     * @param transactionRequest Transaction request that caused the error
     * @return ResponseEntity with error response
     */
    public ResponseEntity<TransactionCreateResponse> handleTransactionCreateException(Exception e, 
                                                                                   TransactionCreateRequest transactionRequest) {
        TransactionCreateResponse response = new TransactionCreateResponse();
        response.setStatus(CICS_RESPONSE_INVALID);
        response.setMessage("Error creating transaction: " + e.getMessage());
        
        if (e.getMessage().contains("already exist")) {
            response.setStatus(CICS_RESPONSE_DUPKEY);
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } else if (e.getMessage().contains("NOT found")) {
            response.setStatus(CICS_RESPONSE_NOTFND);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if (e.getMessage().contains("Invalid")) {
            response.setStatus(CICS_RESPONSE_INVALID);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handles balance calculation exceptions
     * 
     * @param e Exception to handle
     * @param accountId Account ID that caused the error
     * @return ResponseEntity with error response
     */
    public ResponseEntity<BalanceResponse> handleBalanceException(Exception e, String accountId) {
        BalanceResponse response = new BalanceResponse();
        response.setAccountId(accountId);
        response.setBalance(BigDecimal.ZERO);
        response.setStatus(CICS_RESPONSE_INVALID);
        response.setMessage("Error calculating balance for account " + accountId + ": " + e.getMessage());
        
        if (e.getMessage().contains("NOT found")) {
            response.setStatus(CICS_RESPONSE_NOTFND);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } else if (e.getMessage().contains("Invalid")) {
            response.setStatus(CICS_RESPONSE_INVALID);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        } else {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Handles validation exceptions
     * 
     * @param e Exception to handle
     * @param transactionRequest Transaction request that caused the error
     * @return ResponseEntity with error response
     */
    public ResponseEntity<ValidationResponse> handleValidationException(Exception e, 
                                                                       TransactionCreateRequest transactionRequest) {
        ValidationResponse response = new ValidationResponse();
        response.setValid(false);
        response.setStatus(CICS_RESPONSE_INVALID);
        response.setMessage("Validation failed: " + e.getMessage());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // =========================================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================================

    /**
     * Updates navigation context in session
     * 
     * @param request HTTP servlet request
     * @param transactionId CICS transaction ID
     * @param operation Operation being performed
     */
    private void updateNavigationContext(HttpServletRequest request, String transactionId, String operation) {
        NavigationContext navContext = sessionManagementService.getNavigationContext(request);
        navContext.setPreviousScreen(navContext.getCurrentScreen());
        navContext.setCurrentScreen(transactionId);
        navContext.getBreadcrumbs().put(transactionId, operation);
        sessionManagementService.setNavigationContext(request, navContext);
    }

    /**
     * Updates transaction context in session
     * 
     * @param request HTTP servlet request
     * @param transactionPage Page of transactions
     */
    private void updateTransactionContext(HttpServletRequest request, Page<Transaction> transactionPage) {
        TransactionContext txnContext = sessionManagementService.getTransactionContext(request);
        txnContext.setTransactionType("LIST");
        txnContext.getTemporaryData().put("totalElements", transactionPage.getTotalElements());
        txnContext.getTemporaryData().put("currentPage", transactionPage.getNumber());
        txnContext.getTemporaryData().put("totalPages", transactionPage.getTotalPages());
        sessionManagementService.setTransactionContext(request, txnContext);
    }

    /**
     * Updates transaction context with current transaction
     * 
     * @param request HTTP servlet request
     * @param transaction Current transaction
     */
    private void updateTransactionContextWithTransaction(HttpServletRequest request, Transaction transaction) {
        TransactionContext txnContext = sessionManagementService.getTransactionContext(request);
        txnContext.setTransactionId(transaction.getTransactionId());
        txnContext.setTransactionType("DETAIL");
        txnContext.getTemporaryData().put("cardNumber", transaction.getCardNumber());
        txnContext.getTemporaryData().put("accountId", transaction.getAccountId());
        txnContext.getTemporaryData().put("amount", transaction.getTransactionAmount());
        sessionManagementService.setTransactionContext(request, txnContext);
    }

    /**
     * Validates transaction data comprehensively
     * 
     * @param transactionRequest Transaction request to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateTransactionData(TransactionCreateRequest transactionRequest) {
        if (transactionRequest == null) {
            throw new IllegalArgumentException("Transaction request cannot be null");
        }
        
        // Validate card number
        validateCardNumber(transactionRequest.getCardNumber());
        
        // Validate account ID
        validateAccountId(transactionRequest.getAccountId());
        
        // Validate transaction amount
        if (transactionRequest.getTransactionAmount() == null) {
            throw new IllegalArgumentException("Transaction amount cannot be null");
        }
        if (transactionRequest.getTransactionAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Transaction amount cannot be zero");
        }
        
        // Validate transaction type code
        if (transactionRequest.getTransactionTypeCode() == null || 
            transactionRequest.getTransactionTypeCode().length() != 2) {
            throw new IllegalArgumentException("Transaction type code must be exactly 2 characters");
        }
        
        // Validate transaction category code
        if (transactionRequest.getTransactionCategoryCode() == null || 
            transactionRequest.getTransactionCategoryCode().length() != 4) {
            throw new IllegalArgumentException("Transaction category code must be exactly 4 characters");
        }
        
        // Validate merchant information
        if (transactionRequest.getMerchantName() == null || 
            transactionRequest.getMerchantName().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant name cannot be empty");
        }
        
        if (transactionRequest.getMerchantCity() == null || 
            transactionRequest.getMerchantCity().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant city cannot be empty");
        }
        
        if (transactionRequest.getMerchantZip() == null || 
            transactionRequest.getMerchantZip().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ZIP code cannot be empty");
        }
        
        // Validate transaction description
        if (transactionRequest.getTransactionDescription() == null || 
            transactionRequest.getTransactionDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction description cannot be empty");
        }
        
        // Validate transaction source
        if (transactionRequest.getTransactionSource() == null || 
            transactionRequest.getTransactionSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction source cannot be empty");
        }
    }

    /**
     * Validates card number format
     * 
     * @param cardNumber Card number to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            throw new IllegalArgumentException(MSG_INVALID_CARD_NUMBER);
        }
        if (!cardNumber.matches("^[0-9]{16}$")) {
            throw new IllegalArgumentException("Card number must contain only digits");
        }
    }

    /**
     * Validates account ID format
     * 
     * @param accountId Account ID to validate
     * @throws IllegalArgumentException if validation fails
     */
    private void validateAccountId(String accountId) {
        if (accountId == null || accountId.length() != 11) {
            throw new IllegalArgumentException(MSG_INVALID_ACCOUNT_ID);
        }
        if (!accountId.matches("^[0-9]{11}$")) {
            throw new IllegalArgumentException("Account ID must contain only digits");
        }
    }

    /**
     * Builds transaction entity from request
     * 
     * @param transactionRequest Transaction creation request
     * @return Transaction entity
     */
    private Transaction buildTransactionFromRequest(TransactionCreateRequest transactionRequest) {
        Transaction transaction = new Transaction();
        transaction.setCardNumber(transactionRequest.getCardNumber());
        transaction.setAccountId(transactionRequest.getAccountId());
        transaction.setTransactionAmount(transactionRequest.getTransactionAmount());
        transaction.setTransactionTypeCode(transactionRequest.getTransactionTypeCode());
        transaction.setTransactionCategoryCode(transactionRequest.getTransactionCategoryCode());
        transaction.setMerchantName(transactionRequest.getMerchantName());
        transaction.setMerchantCity(transactionRequest.getMerchantCity());
        transaction.setMerchantZip(transactionRequest.getMerchantZip());
        transaction.setTransactionDescription(transactionRequest.getTransactionDescription());
        transaction.setTransactionSource(transactionRequest.getTransactionSource());
        
        // Set timestamps
        LocalDateTime now = LocalDateTime.now();
        transaction.setTransactionTimestamp(now);
        transaction.setProcessedTimestamp(now);
        
        return transaction;
    }

    /**
     * Builds transaction list response from page
     * 
     * @param transactionPage Page of transactions
     * @return Transaction list response
     */
    private TransactionListResponse buildTransactionListResponse(Page<Transaction> transactionPage) {
        TransactionListResponse response = new TransactionListResponse();
        response.setTransactions(transactionPage.getContent());
        response.setPageNumber(transactionPage.getNumber());
        response.setPageSize(transactionPage.getSize());
        response.setTotalElements(transactionPage.getTotalElements());
        response.setTotalPages(transactionPage.getTotalPages());
        response.setFirst(transactionPage.isFirst());
        response.setLast(transactionPage.isLast());
        response.setHasNext(!transactionPage.isLast());
        response.setHasPrevious(!transactionPage.isFirst());
        response.setStatus(CICS_RESPONSE_NORMAL);
        response.setMessage("Transactions retrieved successfully");
        
        return response;
    }

    /**
     * Parses date string to LocalDateTime
     * 
     * @param dateString Date string in YYYY-MM-DD format
     * @return LocalDateTime object
     * @throws IllegalArgumentException if date format is invalid
     */
    private LocalDateTime parseDate(String dateString) {
        try {
            return LocalDateTime.parse(dateString + "T00:00:00", DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date format. Use YYYY-MM-DD format", e);
        }
    }

    /**
     * Masks card number for logging (shows first 4 and last 4 digits)
     * 
     * @param cardNumber Card number to mask
     * @return Masked card number
     */
    private String maskCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return "INVALID";
        }
        return cardNumber.substring(0, 4) + "****" + cardNumber.substring(12);
    }

    // =========================================================================================
    // INNER CLASSES FOR REQUEST/RESPONSE DTOS (MATCHING BMS MAP LAYOUTS)
    // =========================================================================================

    /**
     * Transaction list response DTO matching COTRN0A BMS map layout
     */
    public static class TransactionListResponse {
        private List<Transaction> transactions;
        private int pageNumber;
        private int pageSize;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
        private boolean hasNext;
        private boolean hasPrevious;
        private String status;
        private String message;

        // Getters and setters
        public List<Transaction> getTransactions() { return transactions; }
        public void setTransactions(List<Transaction> transactions) { this.transactions = transactions; }
        public int getPageNumber() { return pageNumber; }
        public void setPageNumber(int pageNumber) { this.pageNumber = pageNumber; }
        public int getPageSize() { return pageSize; }
        public void setPageSize(int pageSize) { this.pageSize = pageSize; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public boolean isFirst() { return first; }
        public void setFirst(boolean first) { this.first = first; }
        public boolean isLast() { return last; }
        public void setLast(boolean last) { this.last = last; }
        public boolean isHasNext() { return hasNext; }
        public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }
        public boolean isHasPrevious() { return hasPrevious; }
        public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Transaction detail response DTO matching COTRN1A BMS map layout
     */
    public static class TransactionDetailResponse {
        private Transaction transaction;
        private String status;
        private String message;

        // Getters and setters
        public Transaction getTransaction() { return transaction; }
        public void setTransaction(Transaction transaction) { this.transaction = transaction; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Transaction creation request DTO matching COTRN2A BMS map layout
     */
    public static class TransactionCreateRequest {
        @NotNull(message = "Card number is required")
        @Size(min = 16, max = 16, message = "Card number must be exactly 16 digits")
        @Pattern(regexp = "^[0-9]{16}$", message = "Card number must contain only digits")
        private String cardNumber;

        @NotNull(message = "Account ID is required")
        @Size(min = 11, max = 11, message = "Account ID must be exactly 11 digits")
        @Pattern(regexp = "^[0-9]{11}$", message = "Account ID must contain only digits")
        private String accountId;

        @NotNull(message = "Transaction amount is required")
        private BigDecimal transactionAmount;

        @NotNull(message = "Transaction type code is required")
        @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
        private String transactionTypeCode;

        @NotNull(message = "Transaction category code is required")
        @Size(min = 4, max = 4, message = "Transaction category code must be exactly 4 characters")
        private String transactionCategoryCode;

        @NotNull(message = "Merchant name is required")
        @Size(min = 1, max = 50, message = "Merchant name must be between 1 and 50 characters")
        private String merchantName;

        @NotNull(message = "Merchant city is required")
        @Size(min = 1, max = 50, message = "Merchant city must be between 1 and 50 characters")
        private String merchantCity;

        @NotNull(message = "Merchant ZIP code is required")
        @Size(min = 5, max = 10, message = "Merchant ZIP code must be between 5 and 10 characters")
        private String merchantZip;

        @NotNull(message = "Transaction description is required")
        @Size(min = 1, max = 100, message = "Transaction description must be between 1 and 100 characters")
        private String transactionDescription;

        @NotNull(message = "Transaction source is required")
        @Size(min = 1, max = 10, message = "Transaction source must be between 1 and 10 characters")
        private String transactionSource;

        private String confirmation = "Y"; // Default confirmation

        // Getters and setters
        public String getCardNumber() { return cardNumber; }
        public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public BigDecimal getTransactionAmount() { return transactionAmount; }
        public void setTransactionAmount(BigDecimal transactionAmount) { this.transactionAmount = transactionAmount; }
        public String getTransactionTypeCode() { return transactionTypeCode; }
        public void setTransactionTypeCode(String transactionTypeCode) { this.transactionTypeCode = transactionTypeCode; }
        public String getTransactionCategoryCode() { return transactionCategoryCode; }
        public void setTransactionCategoryCode(String transactionCategoryCode) { this.transactionCategoryCode = transactionCategoryCode; }
        public String getMerchantName() { return merchantName; }
        public void setMerchantName(String merchantName) { this.merchantName = merchantName; }
        public String getMerchantCity() { return merchantCity; }
        public void setMerchantCity(String merchantCity) { this.merchantCity = merchantCity; }
        public String getMerchantZip() { return merchantZip; }
        public void setMerchantZip(String merchantZip) { this.merchantZip = merchantZip; }
        public String getTransactionDescription() { return transactionDescription; }
        public void setTransactionDescription(String transactionDescription) { this.transactionDescription = transactionDescription; }
        public String getTransactionSource() { return transactionSource; }
        public void setTransactionSource(String transactionSource) { this.transactionSource = transactionSource; }
        public String getConfirmation() { return confirmation; }
        public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
    }

    /**
     * Transaction creation response DTO matching COTRN2A BMS map layout
     */
    public static class TransactionCreateResponse {
        private Transaction transaction;
        private String transactionId;
        private String status;
        private String message;

        // Getters and setters
        public Transaction getTransaction() { return transaction; }
        public void setTransaction(Transaction transaction) { this.transaction = transaction; }
        public String getTransactionId() { return transactionId; }
        public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Balance response DTO
     */
    public static class BalanceResponse {
        private String accountId;
        private BigDecimal balance;
        private String status;
        private String message;

        // Getters and setters
        public String getAccountId() { return accountId; }
        public void setAccountId(String accountId) { this.accountId = accountId; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }

    /**
     * Validation response DTO
     */
    public static class ValidationResponse {
        private boolean valid;
        private String status;
        private String message;

        // Getters and setters
        public boolean isValid() { return valid; }
        public void setValid(boolean valid) { this.valid = valid; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
    }
}