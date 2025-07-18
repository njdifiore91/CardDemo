package com.carddemo.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.carddemo.entity.Account;

import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Spring Data JPA repository interface for Account entity providing CRUD operations 
 * with composite indexes, optimistic locking, and custom query methods.
 * 
 * This repository replaces CICS file control commands for ACCTDAT file operations,
 * implementing PostgreSQL-optimized query patterns with composite indexes that
 * replicate VSAM KSDS key structures for high-performance account data access.
 * 
 * Repository Features:
 * - Primary key-based account retrieval (equivalent to CICS READ with RIDFLD)
 * - Custom query methods for account lookup operations with cross-reference navigation
 * - Pagination support for account listing operations matching COBOL browse patterns
 * - Optimistic locking support with version fields replacing CICS record locking
 * - Composite PostgreSQL indexes for efficient account lookup by customer relationships
 * 
 * Database Schema Integration:
 * - PostgreSQL accounts table with composite indexes on (account_id, customer_id, status)
 * - Cross-reference support through customer_account_xref table navigation
 * - Optimistic locking via version_number field for concurrent access control
 * - BigDecimal monetary precision maintaining exact COBOL COMP-3 accuracy
 * 
 * Original COBOL Operations Mapping:
 * - CICS READ → findByAccountId()
 * - CICS WRITE → save() (for new accounts)
 * - CICS REWRITE → save() (for existing accounts with version check)
 * - CICS DELETE → deleteById()
 * - CICS STARTBR/READNEXT → findAll...() with Pageable
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {

    /**
     * Find account by account ID (primary key lookup).
     * 
     * Replaces CICS READ operation with RIDFLD for direct account access.
     * Uses PostgreSQL primary key index for optimal performance.
     * 
     * @param accountId 11-digit account identifier
     * @return Optional containing account if found, empty otherwise
     */
    Optional<Account> findByAccountId(String accountId);

    /**
     * Find accounts by customer ID for cross-reference navigation.
     * 
     * Supports customer-to-account relationship queries through composite index
     * on (customer_id, account_id) replicating VSAM alternate index access.
     * 
     * @param customerId 9-digit customer identifier
     * @return List of accounts associated with the customer
     */
    @Query("SELECT a FROM Account a WHERE a.accountId IN " +
           "(SELECT cx.accountId FROM CustomerAccountXref cx WHERE cx.customerId = :customerId)")
    List<Account> findByCustomerId(@Param("customerId") String customerId);

    /**
     * Find accounts by active status.
     * 
     * Filters accounts by status code (A=Active, I=Inactive, S=Suspended)
     * using PostgreSQL index on active_status column.
     * 
     * @param activeStatus Single character status code
     * @return List of accounts with matching status
     */
    List<Account> findByActiveStatus(String activeStatus);

    /**
     * Find accounts by customer ID and status with pagination.
     * 
     * Combines customer relationship navigation with status filtering,
     * supporting efficient paginated browse operations.
     * 
     * @param customerId 9-digit customer identifier
     * @param activeStatus Single character status code
     * @param pageable Pagination parameters
     * @return Page of accounts matching criteria
     */
    @Query("SELECT a FROM Account a WHERE a.accountId IN " +
           "(SELECT cx.accountId FROM CustomerAccountXref cx WHERE cx.customerId = :customerId) " +
           "AND a.activeStatus = :activeStatus")
    Page<Account> findAccountsByCustomerIdAndStatus(@Param("customerId") String customerId,
                                                   @Param("activeStatus") String activeStatus,
                                                   Pageable pageable);

    /**
     * Find accounts with current balance greater than specified amount.
     * 
     * Supports financial queries for accounts with balance thresholds,
     * using BigDecimal precision for exact monetary calculations.
     * 
     * @param minimumBalance Minimum current balance threshold
     * @return List of accounts with balance above threshold
     */
    @Query("SELECT a FROM Account a WHERE a.currentBalance > :minimumBalance")
    List<Account> findAccountsWithBalanceGreaterThan(@Param("minimumBalance") BigDecimal minimumBalance);

    /**
     * Find accounts by status and credit limit criteria.
     * 
     * Combines status filtering with credit limit thresholds for
     * account management and reporting operations.
     * 
     * @param activeStatus Single character status code
     * @param minimumCreditLimit Minimum credit limit threshold
     * @return List of accounts matching both criteria
     */
    @Query("SELECT a FROM Account a WHERE a.activeStatus = :activeStatus " +
           "AND a.creditLimit > :minimumCreditLimit")
    List<Account> findAccountsByStatusAndCreditLimitGreaterThan(@Param("activeStatus") String activeStatus,
                                                               @Param("minimumCreditLimit") BigDecimal minimumCreditLimit);

    /**
     * Find accounts with low current balance (potential overlimit scenarios).
     * 
     * Identifies accounts where current balance is negative or approaching
     * credit limit, supporting risk management and alert generation.
     * 
     * @param lowBalanceThreshold Threshold for low balance detection
     * @return List of accounts with concerning balance levels
     */
    @Query("SELECT a FROM Account a WHERE a.currentBalance < :lowBalanceThreshold " +
           "OR a.currentBalance > (a.creditLimit * 0.9)")
    List<Account> findAccountsWithLowBalance(@Param("lowBalanceThreshold") BigDecimal lowBalanceThreshold);

    /**
     * Find accounts by date range (open date, expiration date, reissue date).
     * 
     * Supports date-based account queries for lifecycle management,
     * expiration tracking, and historical analysis.
     * 
     * @param startDate Start date for range query
     * @param endDate End date for range query
     * @return List of accounts within specified date range
     */
    @Query("SELECT a FROM Account a WHERE a.openDate >= :startDate AND a.openDate <= :endDate")
    List<Account> findAccountsByDateRange(@Param("startDate") LocalDate startDate,
                                         @Param("endDate") LocalDate endDate);

    /**
     * Find accounts for cross-reference operations.
     * 
     * Retrieves accounts that participate in card-account relationships,
     * supporting complex queries involving multiple entity associations.
     * 
     * @param cardNumber 16-digit card number for cross-reference lookup
     * @return List of accounts associated with the specified card
     */
    @Query("SELECT a FROM Account a WHERE a.accountId IN " +
           "(SELECT cx.accountId FROM CardAccountXref cx WHERE cx.cardNumber = :cardNumber)")
    List<Account> findAccountsForCrossReference(@Param("cardNumber") String cardNumber);

    /**
     * Count active accounts in the system.
     * 
     * Provides aggregate count of accounts with active status,
     * supporting dashboard and reporting requirements.
     * 
     * @return Total count of active accounts
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.activeStatus = 'A'")
    Long countActiveAccounts();

    /**
     * Count accounts by customer ID.
     * 
     * Returns the number of accounts associated with a specific customer,
     * supporting customer relationship analysis and validation.
     * 
     * @param customerId 9-digit customer identifier
     * @return Count of accounts for the specified customer
     */
    @Query("SELECT COUNT(a) FROM Account a WHERE a.accountId IN " +
           "(SELECT cx.accountId FROM CustomerAccountXref cx WHERE cx.customerId = :customerId)")
    Long countAccountsByCustomerId(@Param("customerId") String customerId);

    /**
     * Find all accounts with pagination support.
     * 
     * Provides paginated access to all account records, supporting
     * administrative browse operations and bulk processing scenarios.
     * 
     * @param pageable Pagination parameters (page number, size, sorting)
     * @return Page of accounts with total count and navigation metadata
     */
    Page<Account> findAll(Pageable pageable);

    /**
     * Find accounts with pagination and sorting (alias for findAll).
     * 
     * Explicit method name for paginated account retrieval, matching
     * the exported interface specification requirements.
     * 
     * @param pageable Pagination parameters (page number, size, sorting)
     * @return Page of accounts with total count and navigation metadata
     */
    default Page<Account> findAllWithPagination(Pageable pageable) {
        return findAll(pageable);
    }

    /**
     * Find accounts by multiple criteria with flexible filtering.
     * 
     * Advanced query method supporting complex account search operations
     * with multiple optional parameters for comprehensive account management.
     * 
     * @param activeStatus Optional status filter
     * @param minimumBalance Optional minimum balance threshold
     * @param customerId Optional customer identifier
     * @param pageable Pagination parameters
     * @return Page of accounts matching the specified criteria
     */
    @Query("SELECT a FROM Account a WHERE " +
           "(:activeStatus IS NULL OR a.activeStatus = :activeStatus) AND " +
           "(:minimumBalance IS NULL OR a.currentBalance >= :minimumBalance) AND " +
           "(:customerId IS NULL OR a.accountId IN " +
           "(SELECT cx.accountId FROM CustomerAccountXref cx WHERE cx.customerId = :customerId))")
    Page<Account> findAccountsByMultipleCriteria(@Param("activeStatus") String activeStatus,
                                                @Param("minimumBalance") BigDecimal minimumBalance,
                                                @Param("customerId") String customerId,
                                                Pageable pageable);

    /**
     * Find expired accounts based on expiration date.
     * 
     * Identifies accounts that have passed their expiration date,
     * supporting account lifecycle management and renewal processes.
     * 
     * @param currentDate Current date for expiration comparison
     * @return List of expired accounts
     */
    @Query("SELECT a FROM Account a WHERE a.expirationDate < :currentDate")
    List<Account> findExpiredAccounts(@Param("currentDate") LocalDate currentDate);

    /**
     * Find accounts requiring reissue based on reissue date.
     * 
     * Identifies accounts that need reissue processing based on
     * reissue date criteria, supporting automated renewal workflows.
     * 
     * @param reissueDate Date threshold for reissue processing
     * @return List of accounts requiring reissue
     */
    @Query("SELECT a FROM Account a WHERE a.reissueDate <= :reissueDate AND a.activeStatus = 'A'")
    List<Account> findAccountsRequiringReissue(@Param("reissueDate") LocalDate reissueDate);

    /**
     * Find accounts by credit limit range.
     * 
     * Supports credit limit analysis and account segmentation operations
     * for business intelligence and marketing campaigns.
     * 
     * @param minCreditLimit Minimum credit limit threshold
     * @param maxCreditLimit Maximum credit limit threshold
     * @return List of accounts within specified credit limit range
     */
    @Query("SELECT a FROM Account a WHERE a.creditLimit >= :minCreditLimit AND a.creditLimit <= :maxCreditLimit")
    List<Account> findAccountsByCreditLimitRange(@Param("minCreditLimit") BigDecimal minCreditLimit,
                                                @Param("maxCreditLimit") BigDecimal maxCreditLimit);

    /**
     * Find accounts by group ID for administrative operations.
     * 
     * Retrieves accounts associated with specific group identifiers,
     * supporting group-based account management and reporting.
     * 
     * @param groupId 10-character group identifier
     * @return List of accounts in the specified group
     */
    List<Account> findByGroupId(String groupId);

    /**
     * Find accounts by address ZIP code for geographic analysis.
     * 
     * Supports geographic-based account analysis and targeted
     * marketing campaigns based on ZIP code demographics.
     * 
     * @param addressZip 5-digit or 9-digit ZIP code
     * @return List of accounts in the specified ZIP code area
     */
    List<Account> findByAddressZip(String addressZip);

    /**
     * Calculate total current balance for all active accounts.
     * 
     * Provides aggregate balance calculation for financial reporting
     * and balance sheet preparation operations.
     * 
     * @return Total current balance across all active accounts
     */
    @Query("SELECT SUM(a.currentBalance) FROM Account a WHERE a.activeStatus = 'A'")
    BigDecimal calculateTotalActiveBalance();

    /**
     * Calculate total credit limit for all active accounts.
     * 
     * Provides aggregate credit limit calculation for risk management
     * and credit exposure analysis operations.
     * 
     * @return Total credit limit across all active accounts
     */
    @Query("SELECT SUM(a.creditLimit) FROM Account a WHERE a.activeStatus = 'A'")
    BigDecimal calculateTotalCreditLimit();

    /**
     * Find accounts with cycle balances exceeding thresholds.
     * 
     * Identifies accounts with high current cycle credit or debit amounts,
     * supporting risk monitoring and exceptional transaction analysis.
     * 
     * @param creditThreshold Threshold for current cycle credit
     * @param debitThreshold Threshold for current cycle debit
     * @return List of accounts exceeding cycle thresholds
     */
    @Query("SELECT a FROM Account a WHERE a.currentCycleCredit > :creditThreshold " +
           "OR a.currentCycleDebit > :debitThreshold")
    List<Account> findAccountsWithHighCycleBalances(@Param("creditThreshold") BigDecimal creditThreshold,
                                                   @Param("debitThreshold") BigDecimal debitThreshold);
}