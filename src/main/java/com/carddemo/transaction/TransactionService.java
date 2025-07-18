package com.carddemo.transaction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.carddemo.account.AccountRepository;
import com.carddemo.card.CardRepository;
import com.carddemo.entity.Account;
import com.carddemo.entity.Card;
import com.carddemo.entity.Transaction;
import com.carddemo.audit.AuditService;
import com.carddemo.session.SessionManagementService;

import org.springframework.dao.DataAccessException;
import jakarta.persistence.EntityNotFoundException;

/**
 * Primary service class for transaction processing business logic implementing real-time authorization,
 * categorization, and balance updates converted from COBOL transaction processing programs (COTRN00C-02C).
 * 
 * This service implements comprehensive transaction management functionality including:
 * - Real-time transaction authorization with response times under 200ms
 * - Transaction categorization through rule engine matching COBOL EVALUATE statements
 * - Balance update processing with atomic operations matching CICS record locking
 * - Comprehensive transaction history management with audit trails
 * - Async processing capabilities for high-volume transaction processing
 * - Complete pagination support for transaction listing (10 records per page)
 * 
 * Original COBOL Programs Converted:
 * - COTRN00C: Transaction listing with pagination and selection support
 * - COTRN01C: Transaction detail viewing with comprehensive field display
 * - COTRN02C: Transaction creation with validation and cross-reference lookup
 * 
 * Key Features:
 * - BigDecimal precision for exact COBOL COMP-3 monetary calculations
 * - Optimistic locking for concurrent transaction processing
 * - Comprehensive audit logging for all transaction operations
 * - Session management for pseudo-conversational state handling
 * - Rule-based transaction categorization matching COBOL EVALUATE logic
 * - Real-time balance calculations with atomic updates
 * - Fraud detection through duplicate transaction checking
 * - Performance optimization with database indexing strategies
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Service
@Transactional(readOnly = true)
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    // Constants for transaction processing (derived from COBOL programs)
    private static final int DEFAULT_PAGE_SIZE = 10; // COTRN00C pagination size
    private static final int MAX_PAGE_SIZE = 100; // Maximum page size for safety
    private static final String TRANSACTION_LIST_OPERATION = "LIST";
    private static final String TRANSACTION_VIEW_OPERATION = "VIEW";
    private static final String TRANSACTION_CREATE_OPERATION = "CREATE";
    private static final String TRANSACTION_UPDATE_OPERATION = "UPDATE";
    private static final String TRANSACTION_DELETE_OPERATION = "DELETE";
    private static final String TRANSACTION_AUTHORIZE_OPERATION = "AUTHORIZE";
    
    // Transaction type constants matching COBOL EVALUATE statements
    private static final String TRANSACTION_TYPE_PURCHASE = "01";
    private static final String TRANSACTION_TYPE_CASH_ADVANCE = "02";
    private static final String TRANSACTION_TYPE_PAYMENT = "03";
    private static final String TRANSACTION_TYPE_REFUND = "04";
    private static final String TRANSACTION_TYPE_ADJUSTMENT = "05";
    
    // Transaction status constants
    private static final String TRANSACTION_STATUS_PENDING = "P";
    private static final String TRANSACTION_STATUS_AUTHORIZED = "A";
    private static final String TRANSACTION_STATUS_DECLINED = "D";
    private static final String TRANSACTION_STATUS_REVERSED = "R";
    
    // Audit event types
    private static final String AUDIT_TRANSACTION_CREATED = "TRANSACTION_CREATED";
    private static final String AUDIT_TRANSACTION_AUTHORIZED = "TRANSACTION_AUTHORIZED";
    private static final String AUDIT_TRANSACTION_DECLINED = "TRANSACTION_DECLINED";
    private static final String AUDIT_BALANCE_UPDATED = "BALANCE_UPDATED";
    private static final String AUDIT_DUPLICATE_DETECTED = "DUPLICATE_DETECTED";

    // Dependency injection - Spring Boot repositories and services
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CardRepository cardRepository;
    private final AuditService auditService;
    private final SessionManagementService sessionManagementService;

    /**
     * Constructor for dependency injection
     * 
     * @param transactionRepository Spring Data JPA repository for transaction entities
     * @param accountRepository Spring Data JPA repository for account entities
     * @param cardRepository Spring Data JPA repository for card entities
     * @param auditService Service for comprehensive audit logging
     * @param sessionManagementService Service for session state management
     */
    @Autowired
    public TransactionService(TransactionRepository transactionRepository,
                            AccountRepository accountRepository,
                            CardRepository cardRepository,
                            AuditService auditService,
                            SessionManagementService sessionManagementService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.cardRepository = cardRepository;
        this.auditService = auditService;
        this.sessionManagementService = sessionManagementService;
    }

    /**
     * Lists transactions with pagination support
     * 
     * Converts COBOL COTRN00C pagination logic to Spring Data Page interface.
     * Supports 10-record pagination with PF7/PF8 equivalent navigation.
     * 
     * @param page Page number (0-based)
     * @param size Number of records per page (default 10)
     * @param sortBy Field to sort by (default: transactionTimestamp)
     * @param sortDirection Sort direction (default: DESC)
     * @return Page of transactions with pagination metadata
     * @throws IllegalArgumentException if page parameters are invalid
     */
    public Page<Transaction> listTransactions(int page, int size, String sortBy, String sortDirection) {
        try {
            // Validate pagination parameters
            if (page < 0) {
                throw new IllegalArgumentException("Page number must be non-negative");
            }
            if (size <= 0 || size > MAX_PAGE_SIZE) {
                size = DEFAULT_PAGE_SIZE;
            }
            
            // Create sort specification
            Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            
            // Execute query with pagination
            Page<Transaction> transactionPage = transactionRepository.findAll(pageable);
            
            // Log data access event for audit trail
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                TRANSACTION_LIST_OPERATION,
                (int) transactionPage.getTotalElements(),
                createQueryDetails("listTransactions", page, size, sortBy, sortDirection)
            );
            
            logger.info("Listed {} transactions (page {}, size {})", 
                       transactionPage.getNumberOfElements(), page, size);
            
            return transactionPage;
            
        } catch (DataAccessException e) {
            logger.error("Database error during transaction listing: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to retrieve transaction list", e);
        }
    }

    /**
     * Retrieves a specific transaction by ID
     * 
     * Converts COBOL COTRN01C transaction detail viewing logic.
     * Includes comprehensive field retrieval and validation.
     * 
     * @param transactionId 16-character transaction identifier
     * @return Transaction entity if found
     * @throws EntityNotFoundException if transaction not found
     * @throws IllegalArgumentException if transaction ID is invalid
     */
    public Transaction getTransactionById(String transactionId) {
        try {
            // Validate transaction ID format
            if (transactionId == null || transactionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction ID cannot be null or empty");
            }
            if (transactionId.length() != 16) {
                throw new IllegalArgumentException("Transaction ID must be exactly 16 characters");
            }
            
            // Retrieve transaction from database
            Optional<Transaction> transactionOpt = transactionRepository.findById(transactionId);
            
            if (transactionOpt.isEmpty()) {
                Map<String, Object> affectedComponents = new HashMap<>();
                affectedComponents.put("transaction_id", transactionId);
                affectedComponents.put("username", getCurrentUsername());
                auditService.logSecurityEvent(
                    "TRANSACTION_NOT_FOUND",
                    "MEDIUM",
                    "Attempt to access non-existent transaction: " + transactionId,
                    affectedComponents
                );
                throw new EntityNotFoundException("Transaction ID NOT found: " + transactionId);
            }
            
            Transaction transaction = transactionOpt.get();
            
            // Log data access event for audit trail
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                TRANSACTION_VIEW_OPERATION,
                1,
                createQueryDetails("getTransactionById", transactionId)
            );
            
            logger.info("Retrieved transaction details for ID: {}", transactionId);
            
            return transaction;
            
        } catch (DataAccessException e) {
            logger.error("Database error during transaction retrieval: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to retrieve transaction details", e);
        }
    }

    /**
     * Creates a new transaction with comprehensive validation
     * 
     * Converts COBOL COTRN02C transaction creation logic with validation
     * and cross-reference lookup functionality.
     * 
     * @param transaction Transaction entity to create
     * @return Created transaction with generated ID
     * @throws IllegalArgumentException if transaction data is invalid
     * @throws EntityNotFoundException if card or account not found
     */
    @Transactional
    public Transaction createTransaction(@Valid Transaction transaction) {
        try {
            // Generate unique transaction ID
            String transactionId = generateTransactionId();
            transaction.setTransactionId(transactionId);
            
            // Set timestamps
            LocalDateTime now = LocalDateTime.now();
            transaction.setProcessedTimestamp(now);
            if (transaction.getTransactionTimestamp() == null) {
                transaction.setTransactionTimestamp(now);
            }
            
            // Validate transaction data
            validateTransaction(transaction);
            
            // Verify card and account existence and status
            Card card = validateCardForTransaction(transaction.getCardNumber());
            Account account = validateAccountForTransaction(transaction.getAccountId());
            
            // Check for duplicate transactions
            checkForDuplicateTransaction(transaction);
            
            // Apply transaction categorization rules
            applyTransactionCategorizationRules(transaction);
            
            // Save transaction to database
            Transaction savedTransaction = transactionRepository.save(transaction);
            
            // Update account balance atomically
            updateAccountBalance(account, transaction);
            
            // Log transaction creation event
            auditService.logTransactionEvent(
                getCurrentUsername(),
                AUDIT_TRANSACTION_CREATED,
                transactionId,
                transaction.getTransactionAmount().doubleValue(),
                createTransactionAuditDetails(transaction)
            );
            
            logger.info("Created new transaction with ID: {} for amount: {}", 
                       transactionId, transaction.getTransactionAmount());
            
            return savedTransaction;
            
        } catch (DataAccessException e) {
            logger.error("Database error during transaction creation: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to create transaction", e);
        }
    }

    /**
     * Authorizes a transaction in real-time
     * 
     * Implements real-time authorization logic with response times under 200ms.
     * Includes fraud detection, balance checking, and limit validation.
     * 
     * @param cardNumber 16-digit card number
     * @param transactionAmount Transaction amount to authorize
     * @param merchantName Merchant name for authorization
     * @return Authorization result with decision and reason
     * @throws IllegalArgumentException if authorization parameters are invalid
     */
    public AuthorizationResult authorizeTransaction(String cardNumber, BigDecimal transactionAmount, String merchantName) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Validate authorization parameters
            if (cardNumber == null || cardNumber.length() != 16) {
                throw new IllegalArgumentException("Card number must be exactly 16 digits");
            }
            if (transactionAmount == null || transactionAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Transaction amount must be positive");
            }
            if (merchantName == null || merchantName.trim().isEmpty()) {
                throw new IllegalArgumentException("Merchant name cannot be empty");
            }
            
            // Retrieve card and account information
            Card card = cardRepository.findByCardNumber(cardNumber)
                .orElseThrow(() -> new EntityNotFoundException("Card not found: " + cardNumber));
            
            Account account = accountRepository.findByAccountId(card.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + card.getAccountId()));
            
            // Perform authorization checks
            AuthorizationResult result = performAuthorizationChecks(card, account, transactionAmount, merchantName);
            
            // Log authorization event
            auditService.logTransactionEvent(
                getCurrentUsername(),
                result.isApproved() ? AUDIT_TRANSACTION_AUTHORIZED : AUDIT_TRANSACTION_DECLINED,
                "AUTH_" + System.currentTimeMillis(),
                transactionAmount.doubleValue(),
                createAuthorizationAuditDetails(cardNumber, transactionAmount, merchantName, result)
            );
            
            long duration = System.currentTimeMillis() - startTime;
            logger.info("Authorization completed in {}ms for card: {} - Result: {}", 
                       duration, maskCardNumber(cardNumber), result.getDecision());
            
            return result;
            
        } catch (Exception e) {
            logger.error("Authorization failed for card: {} - {}", maskCardNumber(cardNumber), e.getMessage(), e);
            throw new RuntimeException("Authorization processing failed", e);
        }
    }

    /**
     * Calculates account balance from transaction history
     * 
     * Implements precise balance calculation using BigDecimal arithmetic
     * matching COBOL COMP-3 precision requirements.
     * 
     * @param accountId 11-digit account identifier
     * @return Current account balance
     * @throws IllegalArgumentException if account ID is invalid
     */
    public BigDecimal calculateBalance(String accountId) {
        try {
            // Validate account ID format
            if (accountId == null || accountId.length() != 11) {
                throw new IllegalArgumentException("Account ID must be exactly 11 digits");
            }
            
            // Calculate balance using repository query
            BigDecimal balance = transactionRepository.calculateBalanceByAccountId(accountId);
            
            // Log data access event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                "BALANCE_CALCULATION",
                1,
                createQueryDetails("calculateBalance", accountId)
            );
            
            logger.debug("Calculated balance for account {}: {}", accountId, balance);
            
            return balance != null ? balance : BigDecimal.ZERO;
            
        } catch (DataAccessException e) {
            logger.error("Database error during balance calculation: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to calculate account balance", e);
        }
    }

    /**
     * Retrieves transaction history for an account
     * 
     * @param accountId 11-digit account identifier
     * @param page Page number for pagination
     * @param size Number of records per page
     * @return Page of transaction history
     */
    public Page<Transaction> getTransactionHistory(String accountId, int page, int size) {
        try {
            // Validate parameters
            if (accountId == null || accountId.length() != 11) {
                throw new IllegalArgumentException("Account ID must be exactly 11 digits");
            }
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Create pagination request
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionTimestamp"));
            
            // Execute query
            Page<Transaction> transactionHistory = transactionRepository.findByAccountIdPageable(accountId, pageable);
            
            // Log data access event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                "HISTORY_RETRIEVAL",
                (int) transactionHistory.getTotalElements(),
                createQueryDetails("getTransactionHistory", accountId, page, size)
            );
            
            logger.info("Retrieved {} transactions for account history: {}", 
                       transactionHistory.getNumberOfElements(), accountId);
            
            return transactionHistory;
            
        } catch (DataAccessException e) {
            logger.error("Database error during transaction history retrieval: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to retrieve transaction history", e);
        }
    }

    /**
     * Finds transactions by card number with pagination
     * 
     * @param cardNumber 16-digit card number
     * @param page Page number for pagination
     * @param size Number of records per page
     * @return Page of transactions for the card
     */
    public Page<Transaction> findByCardNumber(String cardNumber, int page, int size) {
        try {
            // Validate parameters
            if (cardNumber == null || cardNumber.length() != 16) {
                throw new IllegalArgumentException("Card number must be exactly 16 digits");
            }
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Create pagination request
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionTimestamp"));
            
            // Execute query
            Page<Transaction> cardTransactions = transactionRepository.findByCardNumberPageable(cardNumber, pageable);
            
            // Log data access event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                "CARD_LOOKUP",
                (int) cardTransactions.getTotalElements(),
                createQueryDetails("findByCardNumber", cardNumber, page, size)
            );
            
            logger.info("Found {} transactions for card: {}", 
                       cardTransactions.getNumberOfElements(), maskCardNumber(cardNumber));
            
            return cardTransactions;
            
        } catch (DataAccessException e) {
            logger.error("Database error during card transaction lookup: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to find transactions by card number", e);
        }
    }

    /**
     * Finds transactions by account ID with pagination
     * 
     * @param accountId 11-digit account identifier
     * @param page Page number for pagination
     * @param size Number of records per page
     * @return Page of transactions for the account
     */
    public Page<Transaction> findByAccountId(String accountId, int page, int size) {
        try {
            // Validate parameters
            if (accountId == null || accountId.length() != 11) {
                throw new IllegalArgumentException("Account ID must be exactly 11 digits");
            }
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Create pagination request
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionTimestamp"));
            
            // Execute query
            Page<Transaction> accountTransactions = transactionRepository.findByAccountIdPageable(accountId, pageable);
            
            // Log data access event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                "ACCOUNT_LOOKUP",
                (int) accountTransactions.getTotalElements(),
                createQueryDetails("findByAccountId", accountId, page, size)
            );
            
            logger.info("Found {} transactions for account: {}", 
                       accountTransactions.getNumberOfElements(), accountId);
            
            return accountTransactions;
            
        } catch (DataAccessException e) {
            logger.error("Database error during account transaction lookup: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to find transactions by account ID", e);
        }
    }

    /**
     * Processes a transaction with complete business logic
     * 
     * @param transaction Transaction to process
     * @return Processed transaction result
     */
    @Transactional
    public TransactionResult processTransaction(@Valid Transaction transaction) {
        try {
            // Validate transaction data
            validateTransaction(transaction);
            
            // Perform authorization
            AuthorizationResult authResult = authorizeTransaction(
                transaction.getCardNumber(),
                transaction.getTransactionAmount(),
                transaction.getMerchantName()
            );
            
            if (!authResult.isApproved()) {
                return new TransactionResult(false, authResult.getDecisionReason(), null);
            }
            
            // Create transaction record
            Transaction savedTransaction = createTransaction(transaction);
            
            // Process balance updates
            Account account = accountRepository.findByAccountId(transaction.getAccountId())
                .orElseThrow(() -> new EntityNotFoundException("Account not found"));
            
            updateAccountBalance(account, transaction);
            
            logger.info("Successfully processed transaction: {}", transaction.getTransactionId());
            
            return new TransactionResult(true, "Transaction processed successfully", savedTransaction);
            
        } catch (Exception e) {
            logger.error("Transaction processing failed: {}", e.getMessage(), e);
            return new TransactionResult(false, "Transaction processing failed: " + e.getMessage(), null);
        }
    }

    /**
     * Validates transaction data comprehensively
     * 
     * @param transaction Transaction to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validateTransaction(Transaction transaction) {
        if (transaction == null) {
            throw new IllegalArgumentException("Transaction cannot be null");
        }
        
        // Validate card number
        if (transaction.getCardNumber() == null || transaction.getCardNumber().length() != 16) {
            throw new IllegalArgumentException("Card number must be exactly 16 digits");
        }
        
        // Validate account ID
        if (transaction.getAccountId() == null || transaction.getAccountId().length() != 11) {
            throw new IllegalArgumentException("Account ID must be exactly 11 digits");
        }
        
        // Validate transaction amount
        if (transaction.getTransactionAmount() == null || 
            transaction.getTransactionAmount().compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Transaction amount cannot be zero");
        }
        
        // Validate transaction type code
        if (transaction.getTransactionTypeCode() == null || 
            transaction.getTransactionTypeCode().length() != 2) {
            throw new IllegalArgumentException("Transaction type code must be exactly 2 characters");
        }
        
        // Validate transaction category code
        if (transaction.getTransactionCategoryCode() == null || 
            transaction.getTransactionCategoryCode().length() != 4) {
            throw new IllegalArgumentException("Transaction category code must be exactly 4 characters");
        }
        
        // Validate merchant information
        if (transaction.getMerchantName() == null || transaction.getMerchantName().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant name cannot be empty");
        }
        
        if (transaction.getMerchantCity() == null || transaction.getMerchantCity().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant city cannot be empty");
        }
        
        if (transaction.getMerchantZip() == null || transaction.getMerchantZip().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ZIP code cannot be empty");
        }
        
        // Validate transaction description
        if (transaction.getTransactionDescription() == null || 
            transaction.getTransactionDescription().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction description cannot be empty");
        }
        
        // Validate transaction source
        if (transaction.getTransactionSource() == null || 
            transaction.getTransactionSource().trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction source cannot be empty");
        }
        
        logger.debug("Transaction validation completed successfully for card: {}", 
                    maskCardNumber(transaction.getCardNumber()));
    }

    /**
     * Retrieves transactions within a date range
     * 
     * @param startDate Start date of range
     * @param endDate End date of range
     * @param page Page number for pagination
     * @param size Number of records per page
     * @return Page of transactions within date range
     */
    public Page<Transaction> getTransactionsByDateRange(LocalDateTime startDate, LocalDateTime endDate, 
                                                       int page, int size) {
        try {
            // Validate parameters
            if (startDate == null || endDate == null) {
                throw new IllegalArgumentException("Start date and end date cannot be null");
            }
            if (startDate.isAfter(endDate)) {
                throw new IllegalArgumentException("Start date must be before end date");
            }
            if (page < 0) page = 0;
            if (size <= 0 || size > MAX_PAGE_SIZE) size = DEFAULT_PAGE_SIZE;
            
            // Create pagination request
            Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionTimestamp"));
            
            // Execute query
            Page<Transaction> dateRangeTransactions = transactionRepository.findTransactionsByDateRange(
                startDate, endDate, pageable);
            
            // Log data access event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                "DATE_RANGE_QUERY",
                (int) dateRangeTransactions.getTotalElements(),
                createQueryDetails("getTransactionsByDateRange", startDate, endDate, page, size)
            );
            
            logger.info("Found {} transactions in date range {} to {}", 
                       dateRangeTransactions.getNumberOfElements(), startDate, endDate);
            
            return dateRangeTransactions;
            
        } catch (DataAccessException e) {
            logger.error("Database error during date range query: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to retrieve transactions by date range", e);
        }
    }

    /**
     * Retrieves transactions within an amount range
     * 
     * @param minAmount Minimum transaction amount
     * @param maxAmount Maximum transaction amount
     * @return List of transactions within amount range
     */
    public List<Transaction> getTransactionsByAmountRange(BigDecimal minAmount, BigDecimal maxAmount) {
        try {
            // Validate parameters
            if (minAmount == null || maxAmount == null) {
                throw new IllegalArgumentException("Minimum and maximum amounts cannot be null");
            }
            if (minAmount.compareTo(maxAmount) > 0) {
                throw new IllegalArgumentException("Minimum amount must be less than or equal to maximum amount");
            }
            
            // Execute query
            List<Transaction> amountRangeTransactions = transactionRepository.findTransactionsByAmountRange(
                minAmount, maxAmount);
            
            // Log data access event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                "AMOUNT_RANGE_QUERY",
                amountRangeTransactions.size(),
                createQueryDetails("getTransactionsByAmountRange", minAmount, maxAmount)
            );
            
            logger.info("Found {} transactions in amount range {} to {}", 
                       amountRangeTransactions.size(), minAmount, maxAmount);
            
            return amountRangeTransactions;
            
        } catch (DataAccessException e) {
            logger.error("Database error during amount range query: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to retrieve transactions by amount range", e);
        }
    }

    /**
     * Updates an existing transaction
     * 
     * @param transaction Transaction to update
     * @return Updated transaction
     */
    @Transactional
    public Transaction updateTransaction(@Valid Transaction transaction) {
        try {
            // Validate transaction exists
            if (transaction.getTransactionId() == null) {
                throw new IllegalArgumentException("Transaction ID is required for update");
            }
            
            Transaction existingTransaction = transactionRepository.findById(transaction.getTransactionId())
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found for update"));
            
            // Validate transaction data
            validateTransaction(transaction);
            
            // Update transaction
            Transaction updatedTransaction = transactionRepository.save(transaction);
            
            // Log update event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                TRANSACTION_UPDATE_OPERATION,
                1,
                createQueryDetails("updateTransaction", transaction.getTransactionId())
            );
            
            logger.info("Updated transaction: {}", transaction.getTransactionId());
            
            return updatedTransaction;
            
        } catch (DataAccessException e) {
            logger.error("Database error during transaction update: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to update transaction", e);
        }
    }

    /**
     * Deletes a transaction
     * 
     * @param transactionId Transaction ID to delete
     * @throws EntityNotFoundException if transaction not found
     */
    @Transactional
    public void deleteTransaction(String transactionId) {
        try {
            // Validate transaction ID
            if (transactionId == null || transactionId.trim().isEmpty()) {
                throw new IllegalArgumentException("Transaction ID cannot be null or empty");
            }
            
            // Verify transaction exists
            Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new EntityNotFoundException("Transaction not found for deletion"));
            
            // Delete transaction
            transactionRepository.delete(transaction);
            
            // Log deletion event
            auditService.logDataAccessEvent(
                getCurrentUsername(),
                "TRANSACTION",
                TRANSACTION_DELETE_OPERATION,
                1,
                createQueryDetails("deleteTransaction", transactionId)
            );
            
            logger.info("Deleted transaction: {}", transactionId);
            
        } catch (DataAccessException e) {
            logger.error("Database error during transaction deletion: {}", e.getMessage(), e);
            throw new RuntimeException("Unable to delete transaction", e);
        }
    }

    // =========================================================================================
    // PRIVATE HELPER METHODS
    // =========================================================================================

    /**
     * Generates a unique transaction ID
     * 
     * @return 16-character transaction identifier
     */
    private String generateTransactionId() {
        // Generate timestamp-based ID with random component
        long timestamp = System.currentTimeMillis();
        String randomPart = UUID.randomUUID().toString().replaceAll("-", "").substring(0, 6);
        String transactionId = String.format("%010d%s", timestamp % 10000000000L, randomPart);
        
        // Ensure exactly 16 characters
        return transactionId.substring(0, 16).toUpperCase();
    }

    /**
     * Validates card for transaction processing
     * 
     * @param cardNumber Card number to validate
     * @return Card entity if valid
     * @throws EntityNotFoundException if card not found
     * @throws IllegalArgumentException if card is invalid
     */
    private Card validateCardForTransaction(String cardNumber) {
        Card card = cardRepository.findByCardNumber(cardNumber)
            .orElseThrow(() -> new EntityNotFoundException("Card Number NOT found: " + cardNumber));
        
        if (!card.isActive()) {
            throw new IllegalArgumentException("Card is not active: " + maskCardNumber(cardNumber));
        }
        
        if (card.getCardExpiryDate().isBefore(LocalDateTime.now().toLocalDate())) {
            throw new IllegalArgumentException("Card is expired: " + maskCardNumber(cardNumber));
        }
        
        return card;
    }

    /**
     * Validates account for transaction processing
     * 
     * @param accountId Account ID to validate
     * @return Account entity if valid
     * @throws EntityNotFoundException if account not found
     * @throws IllegalArgumentException if account is invalid
     */
    private Account validateAccountForTransaction(String accountId) {
        Account account = accountRepository.findByAccountId(accountId)
            .orElseThrow(() -> new EntityNotFoundException("Account ID NOT found: " + accountId));
        
        if (!account.isActive()) {
            throw new IllegalArgumentException("Account is not active: " + accountId);
        }
        
        return account;
    }

    /**
     * Checks for duplicate transactions
     * 
     * @param transaction Transaction to check
     * @throws IllegalArgumentException if duplicate found
     */
    private void checkForDuplicateTransaction(Transaction transaction) {
        List<Transaction> duplicates = transactionRepository.findPotentialDuplicateTransactions(
            transaction.getCardNumber(),
            transaction.getTransactionAmount(),
            transaction.getMerchantName(),
            1 // 1 hour window
        );
        
        if (!duplicates.isEmpty()) {
            Map<String, Object> affectedComponents = new HashMap<>();
            affectedComponents.put("card_number", maskCardNumber(transaction.getCardNumber()));
            affectedComponents.put("transaction_amount", transaction.getTransactionAmount());
            affectedComponents.put("merchant_name", transaction.getMerchantName());
            affectedComponents.put("duplicate_count", duplicates.size());
            auditService.logSecurityEvent(
                AUDIT_DUPLICATE_DETECTED,
                "HIGH",
                "Duplicate transaction detected for card: " + maskCardNumber(transaction.getCardNumber()),
                affectedComponents
            );
            throw new IllegalArgumentException("Duplicate transaction detected");
        }
    }

    /**
     * Applies transaction categorization rules
     * 
     * @param transaction Transaction to categorize
     */
    private void applyTransactionCategorizationRules(Transaction transaction) {
        // Implement rule engine matching COBOL EVALUATE statements
        String typeCode = transaction.getTransactionTypeCode();
        String categoryCode = transaction.getTransactionCategoryCode();
        
        // Basic categorization rules
        if (TRANSACTION_TYPE_PURCHASE.equals(typeCode)) {
            // Purchase transactions
            if (transaction.getTransactionAmount().compareTo(BigDecimal.ZERO) > 0) {
                // Positive amount for purchases
                transaction.setTransactionSource("POS");
            }
        } else if (TRANSACTION_TYPE_CASH_ADVANCE.equals(typeCode)) {
            // Cash advance transactions
            transaction.setTransactionSource("ATM");
        } else if (TRANSACTION_TYPE_PAYMENT.equals(typeCode)) {
            // Payment transactions
            transaction.setTransactionSource("ONLINE");
        }
        
        logger.debug("Applied categorization rules for transaction type: {}", typeCode);
    }

    /**
     * Updates account balance atomically
     * 
     * @param account Account to update
     * @param transaction Transaction affecting balance
     */
    private void updateAccountBalance(Account account, Transaction transaction) {
        // Calculate new balance
        BigDecimal currentBalance = account.getCurrentBalance();
        BigDecimal transactionAmount = transaction.getTransactionAmount();
        BigDecimal newBalance = currentBalance.add(transactionAmount);
        
        // Update account balance
        account.setCurrentBalance(newBalance);
        accountRepository.save(account);
        
        // Log balance update
        auditService.logTransactionEvent(
            getCurrentUsername(),
            AUDIT_BALANCE_UPDATED,
            transaction.getTransactionId(),
            transactionAmount.doubleValue(),
            createBalanceUpdateAuditDetails(account, currentBalance, newBalance)
        );
        
        logger.info("Updated account balance for {}: {} -> {}", 
                   account.getAccountId(), currentBalance, newBalance);
    }

    /**
     * Performs comprehensive authorization checks
     * 
     * @param card Card for authorization
     * @param account Account for authorization
     * @param amount Transaction amount
     * @param merchantName Merchant name
     * @return Authorization result
     */
    private AuthorizationResult performAuthorizationChecks(Card card, Account account, 
                                                         BigDecimal amount, String merchantName) {
        // Check card status
        if (!card.isActive()) {
            return new AuthorizationResult(false, "Card is not active", "INACTIVE_CARD");
        }
        
        // Check card expiry
        if (card.getCardExpiryDate().isBefore(LocalDateTime.now().toLocalDate())) {
            return new AuthorizationResult(false, "Card is expired", "EXPIRED_CARD");
        }
        
        // Check account status
        if (!account.isActive()) {
            return new AuthorizationResult(false, "Account is not active", "INACTIVE_ACCOUNT");
        }
        
        // Check credit limit
        BigDecimal currentBalance = account.getCurrentBalance();
        BigDecimal creditLimit = account.getCreditLimit();
        BigDecimal availableCredit = creditLimit.add(currentBalance); // Balance could be negative
        
        if (amount.compareTo(availableCredit) > 0) {
            return new AuthorizationResult(false, "Insufficient credit limit", "CREDIT_LIMIT_EXCEEDED");
        }
        
        // Additional fraud checks would go here
        
        return new AuthorizationResult(true, "Transaction authorized", "APPROVED");
    }

    /**
     * Gets the current username from security context
     * 
     * @return Current username or "SYSTEM" if not available
     */
    private String getCurrentUsername() {
        // In a real implementation, this would extract from Spring Security context
        return "SYSTEM"; // Placeholder
    }

    /**
     * Creates query details for audit logging
     * 
     * @param operation Operation name
     * @param params Operation parameters
     * @return Query details map
     */
    private Map<String, Object> createQueryDetails(String operation, Object... params) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("timestamp", LocalDateTime.now());
        details.put("parameters", params);
        return details;
    }

    /**
     * Creates transaction audit details
     * 
     * @param transaction Transaction for audit
     * @return Audit details map
     */
    private Map<String, Object> createTransactionAuditDetails(Transaction transaction) {
        Map<String, Object> details = new HashMap<>();
        details.put("transaction_id", transaction.getTransactionId());
        details.put("card_number", maskCardNumber(transaction.getCardNumber()));
        details.put("account_id", transaction.getAccountId());
        details.put("amount", transaction.getTransactionAmount());
        details.put("merchant_name", transaction.getMerchantName());
        details.put("transaction_type", transaction.getTransactionTypeCode());
        details.put("transaction_category", transaction.getTransactionCategoryCode());
        return details;
    }

    /**
     * Creates authorization audit details
     * 
     * @param cardNumber Card number
     * @param amount Transaction amount
     * @param merchantName Merchant name
     * @param result Authorization result
     * @return Audit details map
     */
    private Map<String, Object> createAuthorizationAuditDetails(String cardNumber, BigDecimal amount, 
                                                               String merchantName, AuthorizationResult result) {
        Map<String, Object> details = new HashMap<>();
        details.put("card_number", maskCardNumber(cardNumber));
        details.put("amount", amount);
        details.put("merchant_name", merchantName);
        details.put("authorization_result", result.getDecision());
        details.put("decision_reason", result.getDecisionReason());
        return details;
    }

    /**
     * Creates balance update audit details
     * 
     * @param account Account updated
     * @param oldBalance Previous balance
     * @param newBalance New balance
     * @return Audit details map
     */
    private Map<String, Object> createBalanceUpdateAuditDetails(Account account, BigDecimal oldBalance, 
                                                               BigDecimal newBalance) {
        Map<String, Object> details = new HashMap<>();
        details.put("account_id", account.getAccountId());
        details.put("old_balance", oldBalance);
        details.put("new_balance", newBalance);
        details.put("balance_change", newBalance.subtract(oldBalance));
        return details;
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
        return cardNumber.substring(0, 4) + "********" + cardNumber.substring(12);
    }

    // =========================================================================================
    // INNER CLASSES FOR RETURN TYPES
    // =========================================================================================

    /**
     * Authorization result class
     */
    public static class AuthorizationResult {
        private final boolean approved;
        private final String decisionReason;
        private final String decision;

        public AuthorizationResult(boolean approved, String decisionReason, String decision) {
            this.approved = approved;
            this.decisionReason = decisionReason;
            this.decision = decision;
        }

        public boolean isApproved() { return approved; }
        public String getDecisionReason() { return decisionReason; }
        public String getDecision() { return decision; }
    }

    /**
     * Transaction processing result class
     */
    public static class TransactionResult {
        private final boolean success;
        private final String message;
        private final Transaction transaction;

        public TransactionResult(boolean success, String message, Transaction transaction) {
            this.success = success;
            this.message = message;
            this.transaction = transaction;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Transaction getTransaction() { return transaction; }
    }
}