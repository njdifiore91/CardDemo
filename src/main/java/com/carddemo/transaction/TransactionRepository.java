package com.carddemo.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.carddemo.entity.Transaction;

import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Spring Data JPA repository interface for transaction data access with query methods 
 * for history, balance calculations, and pagination support replacing VSAM I/O operations.
 * 
 * Converted from COBOL programs:
 * - COTRN00C: Transaction listing with pagination support
 * - COTRN01C: Transaction detail viewing with record access
 * - COTRN02C: Transaction creation with validation and persistence
 * 
 * Implements sophisticated query patterns replicating VSAM KSDS access methods:
 * - STARTBR/READNEXT/READPREV patterns through pagination
 * - Direct record access via primary key lookups
 * - Range queries for date-based filtering
 * - Cross-reference access via card number and account ID
 * 
 * Performance optimizations:
 * - Composite indexes on card_number + transaction_timestamp
 * - Account ID indexing for balance calculations
 * - Optimistic locking support through version control
 * - Batch processing capabilities for high-volume operations
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, String> {

    // =========================================================================================
    // BASIC CRUD OPERATIONS (Spring Data JPA default methods)
    // =========================================================================================
    
    /**
     * Find transaction by ID (inherited from JpaRepository)
     * Replaces COBOL: EXEC CICS READ DATASET('TRANSACT') RIDFLD(TRAN-ID)
     * 
     * @param transactionId 16-character transaction identifier
     * @return Transaction entity or null if not found
     */
    // Optional<Transaction> findById(String transactionId) - provided by JpaRepository
    
    /**
     * Save transaction entity (inherited from JpaRepository)
     * Replaces COBOL: EXEC CICS WRITE DATASET('TRANSACT') FROM(TRAN-RECORD)
     * 
     * @param transaction Transaction entity to save
     * @return Saved transaction entity with updated version
     */
    // Transaction save(Transaction transaction) - provided by JpaRepository
    
    /**
     * Delete transaction entity (inherited from JpaRepository)
     * Replaces COBOL: EXEC CICS DELETE DATASET('TRANSACT') RIDFLD(TRAN-ID)
     * 
     * @param transaction Transaction entity to delete
     */
    // void delete(Transaction transaction) - provided by JpaRepository
    
    /**
     * Find all transactions with pagination (inherited from JpaRepository)
     * Replaces COBOL: STARTBR/READNEXT patterns with pagination
     * 
     * @param pageable Pagination parameters
     * @return Page of transactions
     */
    // Page<Transaction> findAll(Pageable pageable) - provided by JpaRepository
    
    /**
     * Check if transaction exists by ID (inherited from JpaRepository)
     * Replaces COBOL: EXEC CICS READ with NOTFND response check
     * 
     * @param transactionId 16-character transaction identifier
     * @return true if transaction exists, false otherwise
     */
    // boolean existsById(String transactionId) - provided by JpaRepository

    // =========================================================================================
    // CARD NUMBER BASED QUERIES (Primary Access Pattern)
    // =========================================================================================
    
    /**
     * Find all transactions for a specific card number
     * Replaces COBOL: STARTBR with card number key, READNEXT loop
     * Used by COTRN00C for transaction listing by card
     * 
     * @param cardNumber 16-digit card number
     * @return List of transactions for the card
     */
    List<Transaction> findByCardNumber(@Param("cardNumber") String cardNumber);
    
    /**
     * Find transactions by card number with pagination
     * Replaces COBOL: Transaction listing with 10-record pagination from COTRN00C
     * Supports PF7/PF8 key navigation for previous/next page functionality
     * 
     * @param cardNumber 16-digit card number
     * @param pageable Pagination parameters (page size typically 10)
     * @return Page of transactions for the card
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByCardNumberPageable(@Param("cardNumber") String cardNumber, Pageable pageable);
    
    /**
     * Find transactions by card number ordered by timestamp descending
     * Replaces COBOL: READPREV pattern for reverse chronological listing
     * Used in COTRN00C for displaying most recent transactions first
     * 
     * @param cardNumber 16-digit card number
     * @return List of transactions ordered by timestamp (newest first)
     */
    List<Transaction> findByCardNumberOrderByTransactionTimestampDesc(@Param("cardNumber") String cardNumber);
    
    /**
     * Find recent transactions for a card (last 30 days)
     * Optimized query for dashboard and summary displays
     * Replaces COBOL: Date range filtering in transaction listing
     * 
     * @param cardNumber 16-digit card number
     * @param startDate Start date for recent transactions (30 days ago)
     * @return List of recent transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.cardNumber = :cardNumber AND t.transactionTimestamp >= :startDate ORDER BY t.transactionTimestamp DESC")
    List<Transaction> findRecentTransactionsByCardNumber(@Param("cardNumber") String cardNumber, 
                                                        @Param("startDate") LocalDateTime startDate);
    
    /**
     * Count total transactions for a card number
     * Used for pagination calculations and transaction count display
     * Replaces COBOL: Record count calculations in COTRN00C
     * 
     * @param cardNumber 16-digit card number
     * @return Total number of transactions for the card
     */
    long countByCardNumber(@Param("cardNumber") String cardNumber);

    // =========================================================================================
    // ACCOUNT ID BASED QUERIES (Secondary Access Pattern)
    // =========================================================================================
    
    /**
     * Find all transactions for a specific account ID
     * Replaces COBOL: Cross-reference lookup via card-account relationships
     * Used for account-level transaction reporting and balance calculations
     * 
     * @param accountId 11-digit account identifier
     * @return List of transactions for the account
     */
    List<Transaction> findByAccountId(@Param("accountId") String accountId);
    
    /**
     * Find transactions by account ID with pagination
     * Supports account-level transaction history with page navigation
     * Used for account management and customer service functions
     * 
     * @param accountId 11-digit account identifier
     * @param pageable Pagination parameters
     * @return Page of transactions for the account
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findByAccountIdPageable(@Param("accountId") String accountId, Pageable pageable);
    
    /**
     * Find transactions by account ID ordered by timestamp descending
     * Replaces COBOL: Account-level transaction retrieval with sorting
     * Used for account balance calculations and statement generation
     * 
     * @param accountId 11-digit account identifier
     * @return List of transactions ordered by timestamp (newest first)
     */
    List<Transaction> findByAccountIdOrderByTransactionTimestampDesc(@Param("accountId") String accountId);
    
    /**
     * Find recent transactions for an account (last 30 days)
     * Optimized for account summary and balance verification
     * Replaces COBOL: Recent activity reporting in account management
     * 
     * @param accountId 11-digit account identifier
     * @param startDate Start date for recent transactions (30 days ago)
     * @return List of recent transactions
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.transactionTimestamp >= :startDate ORDER BY t.transactionTimestamp DESC")
    List<Transaction> findRecentTransactionsByAccountId(@Param("accountId") String accountId, 
                                                       @Param("startDate") LocalDateTime startDate);
    
    /**
     * Count total transactions for an account ID
     * Used for account activity metrics and pagination calculations
     * Replaces COBOL: Transaction count calculations for account reporting
     * 
     * @param accountId 11-digit account identifier
     * @return Total number of transactions for the account
     */
    long countByAccountId(@Param("accountId") String accountId);

    // =========================================================================================
    // DATE RANGE QUERIES (Temporal Access Patterns)
    // =========================================================================================
    
    /**
     * Find transactions within a specific date range
     * Replaces COBOL: Date range filtering in batch reporting jobs
     * Used for statement generation and transaction history reporting
     * 
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of transactions within the date range
     */
    List<Transaction> findByTransactionTimestampBetween(@Param("startDate") LocalDateTime startDate, 
                                                       @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by card number within date range
     * Replaces COBOL: Card-specific date range queries from COTRN00C
     * Used for card statement generation and transaction history
     * 
     * @param cardNumber 16-digit card number
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of transactions for card within date range
     */
    List<Transaction> findByCardNumberAndTransactionTimestampBetween(@Param("cardNumber") String cardNumber,
                                                                   @Param("startDate") LocalDateTime startDate,
                                                                   @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by account ID within date range
     * Replaces COBOL: Account-specific date range queries for balance calculations
     * Used for account statement generation and balance verification
     * 
     * @param accountId 11-digit account identifier
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @return List of transactions for account within date range
     */
    List<Transaction> findByAccountIdAndTransactionTimestampBetween(@Param("accountId") String accountId,
                                                                   @Param("startDate") LocalDateTime startDate,
                                                                   @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find transactions by date range with pagination
     * Optimized for large date range queries with memory management
     * Replaces COBOL: Batch processing patterns with checkpoint/restart
     * 
     * @param startDate Start of date range (inclusive)
     * @param endDate End of date range (inclusive)
     * @param pageable Pagination parameters
     * @return Page of transactions within date range
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionTimestamp BETWEEN :startDate AND :endDate ORDER BY t.transactionTimestamp DESC")
    Page<Transaction> findTransactionsByDateRange(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate,
                                                 Pageable pageable);

    // =========================================================================================
    // TRANSACTION TYPE AND CATEGORY QUERIES
    // =========================================================================================
    
    /**
     * Find transactions by transaction type code
     * Replaces COBOL: Transaction type filtering in COTRN00C
     * Used for transaction categorization and reporting
     * 
     * @param transactionTypeCode 2-character transaction type code
     * @return List of transactions with specified type
     */
    List<Transaction> findByTransactionTypeCode(@Param("transactionTypeCode") String transactionTypeCode);
    
    /**
     * Find transactions by transaction category code
     * Replaces COBOL: Category-based transaction filtering
     * Used for transaction analysis and category reporting
     * 
     * @param transactionCategoryCode 4-digit transaction category code
     * @return List of transactions with specified category
     */
    List<Transaction> findByTransactionCategoryCode(@Param("transactionCategoryCode") String transactionCategoryCode);

    // =========================================================================================
    // BALANCE CALCULATION QUERIES (Financial Operations)
    // =========================================================================================
    
    /**
     * Calculate total balance for an account ID
     * Replaces COBOL: Balance calculation logic from account management programs
     * Implements precise BigDecimal arithmetic for financial accuracy
     * 
     * @param accountId 11-digit account identifier
     * @return Sum of all transaction amounts for the account
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount), 0) FROM Transaction t WHERE t.accountId = :accountId")
    BigDecimal calculateBalanceByAccountId(@Param("accountId") String accountId);
    
    /**
     * Calculate total balance for a card number
     * Replaces COBOL: Card-specific balance calculations
     * Used for card limit checking and available balance display
     * 
     * @param cardNumber 16-digit card number
     * @return Sum of all transaction amounts for the card
     */
    @Query("SELECT COALESCE(SUM(t.transactionAmount), 0) FROM Transaction t WHERE t.cardNumber = :cardNumber")
    BigDecimal calculateBalanceByCardNumber(@Param("cardNumber") String cardNumber);
    
    /**
     * Find transactions for balance calculation with date cutoff
     * Replaces COBOL: Balance calculation with specific date range
     * Used for statement generation and historical balance verification
     * 
     * @param accountId 11-digit account identifier
     * @param cutoffDate Latest date to include in balance calculation
     * @return List of transactions up to the cutoff date
     */
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId AND t.transactionTimestamp <= :cutoffDate ORDER BY t.transactionTimestamp")
    List<Transaction> findTransactionsForBalanceCalculation(@Param("accountId") String accountId,
                                                           @Param("cutoffDate") LocalDateTime cutoffDate);

    // =========================================================================================
    // AMOUNT RANGE QUERIES (Financial Range Filtering)
    // =========================================================================================
    
    /**
     * Find transactions within specified amount range
     * Replaces COBOL: Amount-based filtering in transaction queries
     * Used for transaction analysis and unusual activity detection
     * 
     * @param minAmount Minimum transaction amount (inclusive)
     * @param maxAmount Maximum transaction amount (inclusive)
     * @return List of transactions within the amount range
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionAmount BETWEEN :minAmount AND :maxAmount ORDER BY t.transactionAmount DESC")
    List<Transaction> findTransactionsByAmountRange(@Param("minAmount") BigDecimal minAmount,
                                                   @Param("maxAmount") BigDecimal maxAmount);

    // =========================================================================================
    // CUSTOM NATIVE QUERIES (Performance-Optimized Operations)
    // =========================================================================================
    
    /**
     * Find transactions with complex search criteria using native SQL
     * Optimized for performance with composite index usage
     * Replaces COBOL: Complex search patterns from COTRN00C with multiple criteria
     * 
     * Performance note: Uses native query for optimal PostgreSQL execution plan
     * 
     * @param cardNumber Optional card number filter
     * @param accountId Optional account ID filter
     * @param startDate Optional start date filter
     * @param endDate Optional end date filter
     * @param transactionTypeCode Optional transaction type filter
     * @param pageable Pagination parameters
     * @return Page of transactions matching search criteria
     */
    @Query(value = "SELECT * FROM transactions t WHERE " +
           "(:cardNumber IS NULL OR t.card_number = :cardNumber) AND " +
           "(:accountId IS NULL OR t.account_id = :accountId) AND " +
           "(:startDate IS NULL OR t.transaction_timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR t.transaction_timestamp <= :endDate) AND " +
           "(:transactionTypeCode IS NULL OR t.transaction_type_cd = :transactionTypeCode) " +
           "ORDER BY t.transaction_timestamp DESC",
           countQuery = "SELECT COUNT(*) FROM transactions t WHERE " +
           "(:cardNumber IS NULL OR t.card_number = :cardNumber) AND " +
           "(:accountId IS NULL OR t.account_id = :accountId) AND " +
           "(:startDate IS NULL OR t.transaction_timestamp >= :startDate) AND " +
           "(:endDate IS NULL OR t.transaction_timestamp <= :endDate) AND " +
           "(:transactionTypeCode IS NULL OR t.transaction_type_cd = :transactionTypeCode)",
           nativeQuery = true)
    Page<Transaction> findTransactionsWithComplexSearch(@Param("cardNumber") String cardNumber,
                                                       @Param("accountId") String accountId,
                                                       @Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate,
                                                       @Param("transactionTypeCode") String transactionTypeCode,
                                                       Pageable pageable);
    
    /**
     * Get transaction statistics for dashboard display
     * Replaces COBOL: Statistical calculations from batch reporting programs
     * Uses native query for optimal aggregation performance
     * 
     * @param accountId 11-digit account identifier
     * @param startDate Start date for statistics calculation
     * @param endDate End date for statistics calculation
     * @return Object array containing count, sum, avg, min, max statistics
     */
    @Query(value = "SELECT COUNT(*), COALESCE(SUM(transaction_amount), 0), " +
           "COALESCE(AVG(transaction_amount), 0), COALESCE(MIN(transaction_amount), 0), " +
           "COALESCE(MAX(transaction_amount), 0) FROM transactions " +
           "WHERE account_id = :accountId AND transaction_timestamp BETWEEN :startDate AND :endDate",
           nativeQuery = true)
    Object[] getTransactionStatistics(@Param("accountId") String accountId,
                                     @Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find duplicate transactions for fraud detection
     * Replaces COBOL: Duplicate detection logic in transaction processing
     * Uses native query for performance optimization
     * 
     * @param cardNumber 16-digit card number
     * @param transactionAmount Transaction amount to check
     * @param merchantName Merchant name to check
     * @param timeWindow Time window for duplicate detection (hours)
     * @return List of potentially duplicate transactions
     */
    @Query(value = "SELECT * FROM transactions WHERE card_number = :cardNumber " +
           "AND transaction_amount = :transactionAmount AND merchant_name = :merchantName " +
           "AND transaction_timestamp >= NOW() - CAST((CAST(:timeWindow AS TEXT) || ' hours') AS INTERVAL) " +
           "ORDER BY transaction_timestamp DESC",
           nativeQuery = true)
    List<Transaction> findPotentialDuplicateTransactions(@Param("cardNumber") String cardNumber,
                                                        @Param("transactionAmount") BigDecimal transactionAmount,
                                                        @Param("merchantName") String merchantName,
                                                        @Param("timeWindow") int timeWindow);
}