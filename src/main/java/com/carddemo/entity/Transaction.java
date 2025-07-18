package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity representing transaction data with BigDecimal precision and optimistic locking
 * converted from COBOL copybook data structures (CVTRA05Y.cpy).
 * 
 * Maintains exact numeric precision during COBOL COMP-3 to Java BigDecimal conversions
 * and implements field validation matching COBOL 88-level conditions.
 * 
 * Database mapping: transactions table with composite indexes for optimal query performance.
 * 
 * Original COBOL structure reference: TRAN-RECORD in CVTRA05Y.cpy (RECLN = 350)
 */
@Entity
@Table(name = "transactions",
       indexes = {
           @Index(name = "idx_transactions_card_timestamp", columnList = "card_number, transaction_timestamp"),
           @Index(name = "idx_transactions_account_timestamp", columnList = "account_id, transaction_timestamp"),
           @Index(name = "idx_transactions_type_category", columnList = "transaction_type_cd, transaction_category_code"),
           @Index(name = "idx_transactions_processed_timestamp", columnList = "processed_timestamp")
       })
public class Transaction {

    /**
     * Transaction ID (16-digit unique identifier)
     * Mapped from COBOL field: TRAN-ID PIC X(16)
     * Primary key with exact 16-character length requirement
     */
    @Id
    @Column(name = "transaction_id", length = 16, nullable = false)
    @NotNull(message = "Transaction ID is required")
    @Size(min = 16, max = 16, message = "Transaction ID must be exactly 16 characters")
    @Pattern(regexp = "^[A-Z0-9]{16}$", message = "Transaction ID must contain only uppercase letters and numbers")
    private String transactionId;

    /**
     * Card number (16-digit foreign key to cards table)
     * Mapped from COBOL field: TRAN-CARD-NUM PIC X(16)
     * Foreign key relationship with cards table
     */
    @Column(name = "card_number", length = 16, nullable = false)
    @NotNull(message = "Card number is required")
    @Size(min = 16, max = 16, message = "Card number must be exactly 16 characters")
    @Pattern(regexp = "^[0-9]{16}$", message = "Card number must contain only digits")
    private String cardNumber;

    /**
     * Account ID (11-digit foreign key to accounts table)
     * Derived from card-account relationship for transaction processing
     * Required for balance updates and account-level transaction tracking
     */
    @Column(name = "account_id", length = 11, nullable = false)
    @NotNull(message = "Account ID is required")
    @Size(min = 11, max = 11, message = "Account ID must be exactly 11 characters")
    @Pattern(regexp = "^[0-9]{11}$", message = "Account ID must contain only digits")
    private String accountId;

    /**
     * Transaction amount with BigDecimal precision
     * Mapped from COBOL field: TRAN-AMT PIC S9(09)V99
     * Uses BigDecimal to preserve exact COBOL COMP-3 numeric precision
     * Scale 2 for monetary amounts (cents precision)
     */
    @Column(name = "transaction_amount", precision = 11, scale = 2, nullable = false)
    @NotNull(message = "Transaction amount is required")
    @Digits(integer = 9, fraction = 2, message = "Transaction amount must have maximum 9 integer digits and 2 decimal places")
    @DecimalMin(value = "-999999999.99", message = "Transaction amount cannot be less than -999999999.99")
    @DecimalMax(value = "999999999.99", message = "Transaction amount cannot exceed 999999999.99")
    private BigDecimal transactionAmount;

    /**
     * Transaction timestamp (original transaction time)
     * Mapped from COBOL field: TRAN-ORIG-TS PIC X(26)
     * Stores the original transaction timestamp from POS/ATM/Online systems
     */
    @Column(name = "transaction_timestamp", nullable = false)
    @NotNull(message = "Transaction timestamp is required")
    private LocalDateTime transactionTimestamp;

    /**
     * Transaction type code (2-character code)
     * Mapped from COBOL field: TRAN-TYPE-CD PIC X(02)
     * References transaction_types table for type description
     */
    @Column(name = "transaction_type_cd", length = 2, nullable = false)
    @NotNull(message = "Transaction type code is required")
    @Size(min = 2, max = 2, message = "Transaction type code must be exactly 2 characters")
    @Pattern(regexp = "^[A-Z0-9]{2}$", message = "Transaction type code must contain only uppercase letters and numbers")
    private String transactionTypeCode;

    /**
     * Transaction category code (4-digit numeric code)
     * Mapped from COBOL field: TRAN-CAT-CD PIC 9(04)
     * References transaction_categories table for category description
     */
    @Column(name = "transaction_category_code", length = 4, nullable = false)
    @NotNull(message = "Transaction category code is required")
    @Size(min = 4, max = 4, message = "Transaction category code must be exactly 4 characters")
    @Pattern(regexp = "^[0-9]{4}$", message = "Transaction category code must contain only digits")
    private String transactionCategoryCode;

    /**
     * Merchant name (up to 50 characters)
     * Mapped from COBOL field: TRAN-MERCHANT-NAME PIC X(50)
     * Stores merchant or transaction originator name
     */
    @Column(name = "merchant_name", length = 50, nullable = false)
    @NotNull(message = "Merchant name is required")
    @Size(min = 1, max = 50, message = "Merchant name must be between 1 and 50 characters")
    private String merchantName;

    /**
     * Merchant city (up to 50 characters)
     * Mapped from COBOL field: TRAN-MERCHANT-CITY PIC X(50)
     * Stores merchant location city
     */
    @Column(name = "merchant_city", length = 50, nullable = false)
    @NotNull(message = "Merchant city is required")
    @Size(min = 1, max = 50, message = "Merchant city must be between 1 and 50 characters")
    private String merchantCity;

    /**
     * Merchant ZIP code (up to 10 characters)
     * Mapped from COBOL field: TRAN-MERCHANT-ZIP PIC X(10)
     * Stores merchant location ZIP/postal code
     */
    @Column(name = "merchant_zip", length = 10, nullable = false)
    @NotNull(message = "Merchant ZIP code is required")
    @Size(min = 5, max = 10, message = "Merchant ZIP code must be between 5 and 10 characters")
    @Pattern(regexp = "^[0-9]{5}(-[0-9]{4})?$", message = "Merchant ZIP code must be in format 12345 or 12345-6789")
    private String merchantZip;

    /**
     * Transaction description (up to 100 characters)
     * Mapped from COBOL field: TRAN-DESC PIC X(100)
     * Detailed description of the transaction
     */
    @Column(name = "transaction_description", length = 100, nullable = false)
    @NotNull(message = "Transaction description is required")
    @Size(min = 1, max = 100, message = "Transaction description must be between 1 and 100 characters")
    private String transactionDescription;

    /**
     * Processed timestamp (when transaction was processed by system)
     * Mapped from COBOL field: TRAN-PROC-TS PIC X(26)
     * System processing timestamp for audit and reconciliation
     */
    @Column(name = "processed_timestamp", nullable = false)
    @NotNull(message = "Processed timestamp is required")
    private LocalDateTime processedTimestamp;

    /**
     * Transaction source (up to 10 characters)
     * Mapped from COBOL field: TRAN-SOURCE PIC X(10)
     * Indicates transaction origination source (POS, ATM, ONLINE, etc.)
     */
    @Column(name = "transaction_source", length = 10, nullable = false)
    @NotNull(message = "Transaction source is required")
    @Size(min = 1, max = 10, message = "Transaction source must be between 1 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "Transaction source must contain only uppercase letters and numbers")
    private String transactionSource;

    /**
     * Version number for optimistic locking
     * Replaces CICS record-level sharing with version-based conflict detection
     * Automatically managed by JPA for concurrent access control
     */
    @Version
    @Column(name = "version_number", nullable = false)
    private Integer versionNumber;

    /**
     * Default constructor for JPA
     */
    public Transaction() {
        // Initialize version number to 0 for new entities
        this.versionNumber = 0;
    }

    /**
     * Constructor with required fields matching COBOL record structure
     * 
     * @param transactionId 16-character transaction identifier
     * @param cardNumber 16-digit card number
     * @param accountId 11-digit account identifier
     * @param transactionAmount monetary amount with 2 decimal places
     * @param transactionTimestamp original transaction time
     * @param transactionTypeCode 2-character type code
     * @param transactionCategoryCode 4-digit category code
     * @param merchantName merchant name
     * @param merchantCity merchant city
     * @param merchantZip merchant ZIP code
     * @param transactionDescription transaction description
     * @param processedTimestamp system processing time
     * @param transactionSource transaction source identifier
     */
    public Transaction(String transactionId, String cardNumber, String accountId, 
                      BigDecimal transactionAmount, LocalDateTime transactionTimestamp,
                      String transactionTypeCode, String transactionCategoryCode,
                      String merchantName, String merchantCity, String merchantZip,
                      String transactionDescription, LocalDateTime processedTimestamp,
                      String transactionSource) {
        this.transactionId = transactionId;
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.transactionAmount = transactionAmount;
        this.transactionTimestamp = transactionTimestamp;
        this.transactionTypeCode = transactionTypeCode;
        this.transactionCategoryCode = transactionCategoryCode;
        this.merchantName = merchantName;
        this.merchantCity = merchantCity;
        this.merchantZip = merchantZip;
        this.transactionDescription = transactionDescription;
        this.processedTimestamp = processedTimestamp;
        this.transactionSource = transactionSource;
        this.versionNumber = 0;
    }

    // Getters and Setters with proper validation and documentation

    /**
     * Gets the transaction ID
     * @return 16-character transaction identifier
     */
    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Sets the transaction ID
     * @param transactionId 16-character transaction identifier
     */
    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Gets the card number
     * @return 16-digit card number
     */
    public String getCardNumber() {
        return cardNumber;
    }

    /**
     * Sets the card number
     * @param cardNumber 16-digit card number
     */
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    /**
     * Gets the account ID
     * @return 11-digit account identifier
     */
    public String getAccountId() {
        return accountId;
    }

    /**
     * Sets the account ID
     * @param accountId 11-digit account identifier
     */
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    /**
     * Gets the transaction amount
     * @return monetary amount with exact precision
     */
    public BigDecimal getTransactionAmount() {
        return transactionAmount;
    }

    /**
     * Sets the transaction amount
     * @param transactionAmount monetary amount with 2 decimal places
     */
    public void setTransactionAmount(BigDecimal transactionAmount) {
        this.transactionAmount = transactionAmount;
    }

    /**
     * Gets the transaction timestamp
     * @return original transaction timestamp
     */
    public LocalDateTime getTransactionTimestamp() {
        return transactionTimestamp;
    }

    /**
     * Sets the transaction timestamp
     * @param transactionTimestamp original transaction time
     */
    public void setTransactionTimestamp(LocalDateTime transactionTimestamp) {
        this.transactionTimestamp = transactionTimestamp;
    }

    /**
     * Gets the transaction type code
     * @return 2-character type code
     */
    public String getTransactionTypeCode() {
        return transactionTypeCode;
    }

    /**
     * Sets the transaction type code
     * @param transactionTypeCode 2-character type code
     */
    public void setTransactionTypeCode(String transactionTypeCode) {
        this.transactionTypeCode = transactionTypeCode;
    }

    /**
     * Gets the transaction category code
     * @return 4-digit category code
     */
    public String getTransactionCategoryCode() {
        return transactionCategoryCode;
    }

    /**
     * Sets the transaction category code
     * @param transactionCategoryCode 4-digit category code
     */
    public void setTransactionCategoryCode(String transactionCategoryCode) {
        this.transactionCategoryCode = transactionCategoryCode;
    }

    /**
     * Gets the merchant name
     * @return merchant name
     */
    public String getMerchantName() {
        return merchantName;
    }

    /**
     * Sets the merchant name
     * @param merchantName merchant name
     */
    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    /**
     * Gets the merchant city
     * @return merchant city
     */
    public String getMerchantCity() {
        return merchantCity;
    }

    /**
     * Sets the merchant city
     * @param merchantCity merchant city
     */
    public void setMerchantCity(String merchantCity) {
        this.merchantCity = merchantCity;
    }

    /**
     * Gets the merchant ZIP code
     * @return merchant ZIP code
     */
    public String getMerchantZip() {
        return merchantZip;
    }

    /**
     * Sets the merchant ZIP code
     * @param merchantZip merchant ZIP code
     */
    public void setMerchantZip(String merchantZip) {
        this.merchantZip = merchantZip;
    }

    /**
     * Gets the transaction description
     * @return detailed transaction description
     */
    public String getTransactionDescription() {
        return transactionDescription;
    }

    /**
     * Sets the transaction description
     * @param transactionDescription detailed transaction description
     */
    public void setTransactionDescription(String transactionDescription) {
        this.transactionDescription = transactionDescription;
    }

    /**
     * Gets the processed timestamp
     * @return system processing timestamp
     */
    public LocalDateTime getProcessedTimestamp() {
        return processedTimestamp;
    }

    /**
     * Sets the processed timestamp
     * @param processedTimestamp system processing time
     */
    public void setProcessedTimestamp(LocalDateTime processedTimestamp) {
        this.processedTimestamp = processedTimestamp;
    }

    /**
     * Gets the transaction source
     * @return transaction source identifier
     */
    public String getTransactionSource() {
        return transactionSource;
    }

    /**
     * Sets the transaction source
     * @param transactionSource transaction source identifier
     */
    public void setTransactionSource(String transactionSource) {
        this.transactionSource = transactionSource;
    }

    /**
     * Gets the version number for optimistic locking
     * @return version number
     */
    public Integer getVersionNumber() {
        return versionNumber;
    }

    /**
     * Sets the version number for optimistic locking
     * @param versionNumber version number
     */
    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    /**
     * Indicates whether this transaction is a debit (negative amount)
     * Matches COBOL 88-level condition logic
     * @return true if transaction amount is negative
     */
    public boolean isDebit() {
        return transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Indicates whether this transaction is a credit (positive amount)
     * Matches COBOL 88-level condition logic
     * @return true if transaction amount is positive
     */
    public boolean isCredit() {
        return transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Indicates whether this transaction is a zero-amount transaction
     * Matches COBOL 88-level condition logic
     * @return true if transaction amount is zero
     */
    public boolean isZeroAmount() {
        return transactionAmount != null && transactionAmount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Gets the absolute value of the transaction amount
     * Useful for display purposes regardless of debit/credit
     * @return absolute transaction amount
     */
    public BigDecimal getAbsoluteAmount() {
        return transactionAmount != null ? transactionAmount.abs() : BigDecimal.ZERO;
    }

    /**
     * Checks if the transaction was processed today
     * @return true if processed timestamp is today
     */
    public boolean isProcessedToday() {
        if (processedTimestamp == null) return false;
        return processedTimestamp.toLocalDate().equals(LocalDateTime.now().toLocalDate());
    }

    /**
     * Equality comparison based on transaction ID
     * Implements proper JPA entity identity management
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Transaction that = (Transaction) obj;
        return Objects.equals(transactionId, that.transactionId);
    }

    /**
     * Hash code based on transaction ID
     * Ensures proper collection framework compatibility
     */
    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }

    /**
     * String representation for debugging and logging
     * Includes key transaction details without sensitive information
     */
    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", cardNumber='" + (cardNumber != null ? cardNumber.substring(0, 4) + "****" + cardNumber.substring(12) : "null") + '\'' +
                ", accountId='" + accountId + '\'' +
                ", transactionAmount=" + transactionAmount +
                ", transactionTimestamp=" + transactionTimestamp +
                ", transactionTypeCode='" + transactionTypeCode + '\'' +
                ", transactionCategoryCode='" + transactionCategoryCode + '\'' +
                ", merchantName='" + merchantName + '\'' +
                ", merchantCity='" + merchantCity + '\'' +
                ", merchantZip='" + merchantZip + '\'' +
                ", transactionSource='" + transactionSource + '\'' +
                ", versionNumber=" + versionNumber +
                '}';
    }
}