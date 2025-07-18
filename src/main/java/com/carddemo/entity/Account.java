package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity class representing account records with exact COBOL field mapping.
 * 
 * This entity maps the COBOL ACCOUNT-RECORD structure from CVACT01Y copybook
 * to the PostgreSQL accounts table, maintaining exact field preservation and
 * COBOL business logic compatibility.
 * 
 * Original COBOL Structure:
 * - ACCT-ID (PIC 9(11)) -> accountId (String)
 * - ACCT-ACTIVE-STATUS (PIC X(01)) -> activeStatus (String)
 * - ACCT-CURR-BAL (PIC S9(10)V99) -> currentBalance (BigDecimal)
 * - ACCT-CREDIT-LIMIT (PIC S9(10)V99) -> creditLimit (BigDecimal)
 * - ACCT-CASH-CREDIT-LIMIT (PIC S9(10)V99) -> cashCreditLimit (BigDecimal)
 * - ACCT-OPEN-DATE (PIC X(10)) -> openDate (LocalDate)
 * - ACCT-EXPIRAION-DATE (PIC X(10)) -> expirationDate (LocalDate)
 * - ACCT-REISSUE-DATE (PIC X(10)) -> reissueDate (LocalDate)
 * - ACCT-CURR-CYC-CREDIT (PIC S9(10)V99) -> currentCycleCredit (BigDecimal)
 * - ACCT-CURR-CYC-DEBIT (PIC S9(10)V99) -> currentCycleDebit (BigDecimal)
 * - ACCT-ADDR-ZIP (PIC X(10)) -> addressZip (String)
 * - ACCT-GROUP-ID (PIC X(10)) -> groupId (String)
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_accounts_customer_id", columnList = "account_id"),
    @Index(name = "idx_accounts_status", columnList = "active_status"),
    @Index(name = "idx_accounts_group_id", columnList = "group_id")
})
public class Account implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Primary key account identifier (11-digit numeric format)
     * Maps to COBOL ACCT-ID PIC 9(11) field
     */
    @Id
    @Column(name = "account_id", length = 11, nullable = false)
    @NotNull(message = "Account ID cannot be null")
    @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
    private String accountId;
    
    /**
     * Account active status flag (single character)
     * Maps to COBOL ACCT-ACTIVE-STATUS PIC X(01) field
     */
    @Column(name = "account_status", length = 1, nullable = false)
    @NotNull(message = "Active status cannot be null")
    @Pattern(regexp = "^[AIS]$", message = "Active status must be A (Active), I (Inactive), or S (Suspended)")
    private String activeStatus;
    
    /**
     * Current account balance with exact COBOL COMP-3 precision
     * Maps to COBOL ACCT-CURR-BAL PIC S9(10)V99 field
     */
    @Column(name = "current_balance", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Current balance cannot be null")
    @Digits(integer = 10, fraction = 2, message = "Current balance must have maximum 10 integer digits and 2 decimal places")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal currentBalance;
    
    /**
     * Credit limit for the account with COBOL COMP-3 precision
     * Maps to COBOL ACCT-CREDIT-LIMIT PIC S9(10)V99 field
     */
    @Column(name = "credit_limit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Credit limit cannot be null")
    @Digits(integer = 10, fraction = 2, message = "Credit limit must have maximum 10 integer digits and 2 decimal places")
    @Min(value = 0, message = "Credit limit must be non-negative")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal creditLimit;
    
    /**
     * Cash credit limit for the account with COBOL COMP-3 precision
     * Maps to COBOL ACCT-CASH-CREDIT-LIMIT PIC S9(10)V99 field
     */
    @Column(name = "cash_credit_limit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Cash credit limit cannot be null")
    @Digits(integer = 10, fraction = 2, message = "Cash credit limit must have maximum 10 integer digits and 2 decimal places")
    @Min(value = 0, message = "Cash credit limit must be non-negative")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal cashCreditLimit;
    
    /**
     * Account opening date with YYYY-MM-DD format validation
     * Maps to COBOL ACCT-OPEN-DATE PIC X(10) field
     */
    @Column(name = "account_open_date", nullable = false)
    @NotNull(message = "Open date cannot be null")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate openDate;
    
    /**
     * Account expiration date with YYYY-MM-DD format validation
     * Maps to COBOL ACCT-EXPIRAION-DATE PIC X(10) field (note: preserving original typo)
     */
    @Column(name = "expiry_date", nullable = false)
    @NotNull(message = "Expiration date cannot be null")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate expirationDate;
    
    /**
     * Account reissue date with YYYY-MM-DD format validation
     * Maps to COBOL ACCT-REISSUE-DATE PIC X(10) field
     */
    @Column(name = "reissue_date", nullable = false)
    @NotNull(message = "Reissue date cannot be null")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate reissueDate;
    
    /**
     * Current cycle credit amount with COBOL COMP-3 precision
     * Maps to COBOL ACCT-CURR-CYC-CREDIT PIC S9(10)V99 field
     */
    @Column(name = "current_cycle_credit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Current cycle credit cannot be null")
    @Digits(integer = 10, fraction = 2, message = "Current cycle credit must have maximum 10 integer digits and 2 decimal places")
    @Min(value = 0, message = "Current cycle credit must be non-negative")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal currentCycleCredit;
    
    /**
     * Current cycle debit amount with COBOL COMP-3 precision
     * Maps to COBOL ACCT-CURR-CYC-DEBIT PIC S9(10)V99 field
     */
    @Column(name = "current_cycle_debit", precision = 12, scale = 2, nullable = false)
    @NotNull(message = "Current cycle debit cannot be null")
    @Digits(integer = 10, fraction = 2, message = "Current cycle debit must have maximum 10 integer digits and 2 decimal places")
    @Min(value = 0, message = "Current cycle debit must be non-negative")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "0.00")
    private BigDecimal currentCycleDebit;
    
    /**
     * Address zip code (10-character string)
     * Maps to COBOL ACCT-ADDR-ZIP PIC X(10) field
     */
    @Column(name = "address_zip", length = 10, nullable = false)
    @NotNull(message = "Address zip cannot be null")
    @Pattern(regexp = "^\\d{5}(-\\d{4})?$|^\\d{10}$", message = "Address zip must be 5 digits, 9 digits, or 5+4 format")
    private String addressZip;
    
    /**
     * Group identifier for account classification
     * Maps to COBOL ACCT-GROUP-ID PIC X(10) field
     */
    @Column(name = "group_id", length = 10, nullable = false)
    @NotNull(message = "Group ID cannot be null")
    @Size(min = 1, max = 10, message = "Group ID must be between 1 and 10 characters")
    private String groupId;
    
    /**
     * Optimistic locking version field for concurrent access control
     * Replaces CICS record sharing mechanisms with JPA-based optimistic locking
     */
    @Version
    @Column(name = "row_version")
    private Integer versionNumber;
    
    @Column(name = "customer_id", length = 9, nullable = false)
    @NotNull(message = "Customer ID cannot be null")
    @Pattern(regexp = "^\\d{9}$", message = "Customer ID must be exactly 9 digits")
    private String customerId;

    @Column(name = "available_credit", precision = 15, scale = 2, nullable = false)
    @NotNull(message = "Available credit cannot be null")
    private BigDecimal availableCredit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Default constructor for JPA
     */
    public Account() {
        // Initialize monetary fields to zero for safety
        this.currentBalance = BigDecimal.ZERO.setScale(2);
        this.creditLimit = BigDecimal.ZERO.setScale(2);
        this.cashCreditLimit = BigDecimal.ZERO.setScale(2);
        this.currentCycleCredit = BigDecimal.ZERO.setScale(2);
        this.currentCycleDebit = BigDecimal.ZERO.setScale(2);
    }
    
    /**
     * Constructor with required fields
     * 
     * @param accountId 11-digit account identifier
     * @param activeStatus account status (A/I/S)
     * @param currentBalance current account balance
     * @param creditLimit credit limit amount
     * @param cashCreditLimit cash credit limit amount
     * @param openDate account opening date
     * @param expirationDate account expiration date
     * @param reissueDate account reissue date
     * @param currentCycleCredit current cycle credit amount
     * @param currentCycleDebit current cycle debit amount
     * @param addressZip address zip code
     * @param groupId group identifier
     */
    public Account(String accountId, String activeStatus, BigDecimal currentBalance, 
                  BigDecimal creditLimit, BigDecimal cashCreditLimit, LocalDate openDate,
                  LocalDate expirationDate, LocalDate reissueDate, BigDecimal currentCycleCredit,
                  BigDecimal currentCycleDebit, String addressZip, String groupId) {
        this.accountId = accountId;
        this.activeStatus = activeStatus;
        this.currentBalance = currentBalance != null ? currentBalance.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.creditLimit = creditLimit != null ? creditLimit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.cashCreditLimit = cashCreditLimit != null ? cashCreditLimit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.openDate = openDate;
        this.expirationDate = expirationDate;
        this.reissueDate = reissueDate;
        this.currentCycleCredit = currentCycleCredit != null ? currentCycleCredit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.currentCycleDebit = currentCycleDebit != null ? currentCycleDebit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
        this.addressZip = addressZip;
        this.groupId = groupId;
    }
    
    // Getter and Setter methods
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getActiveStatus() {
        return activeStatus;
    }
    
    public void setActiveStatus(String activeStatus) {
        this.activeStatus = activeStatus;
    }
    
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }
    
    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance != null ? currentBalance.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
    
    public BigDecimal getCreditLimit() {
        return creditLimit;
    }
    
    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit != null ? creditLimit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
    
    public BigDecimal getCashCreditLimit() {
        return cashCreditLimit;
    }
    
    public void setCashCreditLimit(BigDecimal cashCreditLimit) {
        this.cashCreditLimit = cashCreditLimit != null ? cashCreditLimit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
    
    public LocalDate getOpenDate() {
        return openDate;
    }
    
    public void setOpenDate(LocalDate openDate) {
        this.openDate = openDate;
    }
    
    public LocalDate getExpirationDate() {
        return expirationDate;
    }
    
    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }
    
    public LocalDate getReissueDate() {
        return reissueDate;
    }
    
    public void setReissueDate(LocalDate reissueDate) {
        this.reissueDate = reissueDate;
    }
    
    public BigDecimal getCurrentCycleCredit() {
        return currentCycleCredit;
    }
    
    public void setCurrentCycleCredit(BigDecimal currentCycleCredit) {
        this.currentCycleCredit = currentCycleCredit != null ? currentCycleCredit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
    
    public BigDecimal getCurrentCycleDebit() {
        return currentCycleDebit;
    }
    
    public void setCurrentCycleDebit(BigDecimal currentCycleDebit) {
        this.currentCycleDebit = currentCycleDebit != null ? currentCycleDebit.setScale(2, java.math.RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2);
    }
    
    public String getAddressZip() {
        return addressZip;
    }
    
    public void setAddressZip(String addressZip) {
        this.addressZip = addressZip;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public Integer getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }
    
    public String getCustomerId() {
		return customerId;
	}

	public void setCustomerId(String customerId) {
		this.customerId = customerId;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public void setAvailableCredit(BigDecimal availableCredit) {
		this.availableCredit = availableCredit;
	}

	/**
     * Business logic method to check if account is active
     * Replicates COBOL 88-level condition logic
     * 
     * @return true if account status is 'A' (Active), false otherwise
     */
    public boolean isActive() {
        return "A".equals(activeStatus);
    }
    
    /**
     * Business logic method to check if account is inactive
     * Replicates COBOL 88-level condition logic
     * 
     * @return true if account status is 'I' (Inactive), false otherwise
     */
    public boolean isInactive() {
        return "I".equals(activeStatus);
    }
    
    /**
     * Business logic method to check if account is suspended
     * Replicates COBOL 88-level condition logic
     * 
     * @return true if account status is 'S' (Suspended), false otherwise
     */
    public boolean isSuspended() {
        return "S".equals(activeStatus);
    }
    
    /**
     * Business logic method to calculate available credit
     * Maintains COBOL-style decimal arithmetic precision
     * 
     * @return available credit amount (credit limit - current balance)
     */
    public BigDecimal getAvailableCredit() {
        if (creditLimit == null || currentBalance == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return creditLimit.subtract(currentBalance).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Business logic method to calculate available cash credit
     * Maintains COBOL-style decimal arithmetic precision
     * 
     * @return available cash credit amount
     */
    public BigDecimal getAvailableCashCredit() {
        if (cashCreditLimit == null || currentBalance == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return cashCreditLimit.subtract(currentBalance).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Business logic method to check if account is expired
     * Replicates COBOL date comparison logic
     * 
     * @return true if account is expired based on expiration date
     */
    public boolean isExpired() {
        return expirationDate != null && expirationDate.isBefore(LocalDate.now());
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Account account = (Account) o;
        return Objects.equals(accountId, account.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(accountId);
    }
    
    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", activeStatus='" + activeStatus + '\'' +
                ", currentBalance=" + currentBalance +
                ", creditLimit=" + creditLimit +
                ", cashCreditLimit=" + cashCreditLimit +
                ", openDate=" + openDate +
                ", expirationDate=" + expirationDate +
                ", reissueDate=" + reissueDate +
                ", currentCycleCredit=" + currentCycleCredit +
                ", currentCycleDebit=" + currentCycleDebit +
                ", addressZip='" + addressZip + '\'' +
                ", groupId='" + groupId + '\'' +
                ", versionNumber=" + versionNumber +
                '}';
    }
}