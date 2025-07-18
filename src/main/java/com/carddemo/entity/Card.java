package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * JPA Entity class representing card records with exact COBOL field mapping.
 * 
 * This entity maps the COBOL CARD-RECORD structure from CVACT02Y copybook
 * to the PostgreSQL cards table, maintaining exact field preservation and
 * COBOL business logic compatibility.
 * 
 * Original COBOL Structure:
 * - CARD-NUM (PIC X(16)) -> cardNumber (String)
 * - CARD-ACCT-ID (PIC 9(11)) -> accountId (String)
 * - CARD-CVV-CD (PIC 9(03)) -> cardCvvCode (String)
 * - CARD-EMBOSSED-NAME (PIC X(50)) -> embossedName (String)
 * - CARD-EXPIRAION-DATE (PIC X(10)) -> cardExpiryDate (LocalDate)
 * - CARD-ACTIVE-STATUS (PIC X(01)) -> cardStatus (String)
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "cards", indexes = {
    @Index(name = "idx_cards_account_id", columnList = "account_id"),
    @Index(name = "idx_cards_status", columnList = "card_status"),
    @Index(name = "idx_cards_expiry_date", columnList = "card_expiry_date")
})
public class Card implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Primary key card number (16-digit numeric format)
     * Maps to COBOL CARD-NUM PIC X(16) field
     */
    @Id
    @Column(name = "card_number", length = 16, nullable = false)
    @NotNull(message = "Card number cannot be null")
    @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
    private String cardNumber;
    
    /**
     * Account ID foreign key reference (11-digit numeric format)
     * Maps to COBOL CARD-ACCT-ID PIC 9(11) field
     */
    @Column(name = "account_id", length = 11, nullable = false)
    @NotNull(message = "Account ID cannot be null")
    @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
    private String accountId;
    
    /**
     * Card CVV security code (3-digit numeric format)
     * Maps to COBOL CARD-CVV-CD PIC 9(03) field
     */
    @Column(name = "card_cvv_code", length = 3, nullable = false)
    @NotNull(message = "CVV code cannot be null")
    @Pattern(regexp = "^\\d{3}$", message = "CVV code must be exactly 3 digits")
    private String cardCvvCode;
    
    /**
     * Card embossed name (50-character maximum)
     * Maps to COBOL CARD-EMBOSSED-NAME PIC X(50) field
     */
    @Column(name = "embossed_name", length = 50, nullable = false)
    @NotNull(message = "Embossed name cannot be null")
    @Size(min = 1, max = 50, message = "Embossed name must be between 1 and 50 characters")
    private String embossedName;
    
    /**
     * Card expiration date with YYYY-MM-DD format validation
     * Maps to COBOL CARD-EXPIRAION-DATE PIC X(10) field (note: preserving original typo)
     */
    @Column(name = "card_expiry_date", nullable = false)
    @NotNull(message = "Card expiry date cannot be null")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate cardExpiryDate;
    
    /**
     * Card active status flag (single character)
     * Maps to COBOL CARD-ACTIVE-STATUS PIC X(01) field
     */
    @Column(name = "card_status", length = 1, nullable = false)
    @NotNull(message = "Card status cannot be null")
    @Pattern(regexp = "^[AIS]$", message = "Card status must be A (Active), I (Inactive), or S (Suspended)")
    private String cardStatus;
    
    /**
     * Card type classification
     * Additional field for card type management
     */
    @Column(name = "card_type", length = 10, nullable = false)
    @NotNull(message = "Card type cannot be null")
    @Pattern(regexp = "^(CREDIT|DEBIT|PREPAID)$", message = "Card type must be CREDIT, DEBIT, or PREPAID")
    private String cardType;
    
    /**
     * Foreign key relationship to Account entity
     * Enables navigation from Card to Account for business operations
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", insertable = false, updatable = false)
    private Account account;
    
    /**
     * Optimistic locking version field for concurrent access control
     * Replaces CICS record sharing mechanisms with JPA-based optimistic locking
     */
    @Version
    @Column(name = "row_version")
    private Integer versionNumber;
    
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
    public Card() {
        // Default constructor for JPA entity instantiation
    }
    
    /**
     * Constructor with required fields
     * 
     * @param cardNumber 16-digit card number
     * @param accountId 11-digit account identifier
     * @param cardCvvCode 3-digit CVV code
     * @param embossedName cardholder name
     * @param cardExpiryDate card expiration date
     * @param cardStatus card status (A/I/S)
     * @param cardType card type (CREDIT/DEBIT/PREPAID)
     */
    public Card(String cardNumber, String accountId, String cardCvvCode, 
                String embossedName, LocalDate cardExpiryDate, String cardStatus, String cardType) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
        this.cardCvvCode = cardCvvCode;
        this.embossedName = embossedName;
        this.cardExpiryDate = cardExpiryDate;
        this.cardStatus = cardStatus;
        this.cardType = cardType;
    }
    
    // Getter and Setter methods
    
    public String getCardNumber() {
        return cardNumber;
    }
    
    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }
    
    public String getCardCvvCode() {
        return cardCvvCode;
    }
    
    public void setCardCvvCode(String cardCvvCode) {
        this.cardCvvCode = cardCvvCode;
    }
    
    public String getEmbossedName() {
        return embossedName;
    }
    
    public void setEmbossedName(String embossedName) {
        this.embossedName = embossedName;
    }
    
    public LocalDate getCardExpiryDate() {
        return cardExpiryDate;
    }
    
    public void setCardExpiryDate(LocalDate cardExpiryDate) {
        this.cardExpiryDate = cardExpiryDate;
    }
    
    public String getCardStatus() {
        return cardStatus;
    }
    
    public void setCardStatus(String cardStatus) {
        this.cardStatus = cardStatus;
    }
    
    public String getCardType() {
        return cardType;
    }
    
    public void setCardType(String cardType) {
        this.cardType = cardType;
    }
    
    public Account getAccount() {
        return account;
    }
    
    public void setAccount(Account account) {
        this.account = account;
    }
    
    public Integer getVersionNumber() {
        return versionNumber;
    }
    
    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
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

	/**
     * Business logic method to check if card is active
     * Replicates COBOL 88-level condition logic
     * 
     * @return true if card status is 'A' (Active), false otherwise
     */
    public boolean isActive() {
        return "A".equals(cardStatus);
    }
    
    /**
     * Business logic method to check if card is inactive
     * Replicates COBOL 88-level condition logic
     * 
     * @return true if card status is 'I' (Inactive), false otherwise
     */
    public boolean isInactive() {
        return "I".equals(cardStatus);
    }
    
    /**
     * Business logic method to check if card is suspended
     * Replicates COBOL 88-level condition logic
     * 
     * @return true if card status is 'S' (Suspended), false otherwise
     */
    public boolean isSuspended() {
        return "S".equals(cardStatus);
    }
    
    /**
     * Business logic method to check if card is expired
     * Replicates COBOL date comparison logic
     * 
     * @return true if card is expired based on expiry date
     */
    public boolean isExpired() {
        return cardExpiryDate != null && cardExpiryDate.isBefore(LocalDate.now());
    }
    
    /**
     * Business logic method to check if card is a credit card
     * 
     * @return true if card type is CREDIT
     */
    public boolean isCreditCard() {
        return "CREDIT".equals(cardType);
    }
    
    /**
     * Business logic method to check if card is a debit card
     * 
     * @return true if card type is DEBIT
     */
    public boolean isDebitCard() {
        return "DEBIT".equals(cardType);
    }
    
    /**
     * Business logic method to check if card is a prepaid card
     * 
     * @return true if card type is PREPAID
     */
    public boolean isPrepaidCard() {
        return "PREPAID".equals(cardType);
    }
    
    /**
     * Business logic method to validate card number using Luhn algorithm
     * Replicates COBOL card validation logic
     * 
     * @return true if card number passes Luhn validation
     */
    public boolean isValidCardNumber() {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        
        // Luhn algorithm implementation
        int sum = 0;
        boolean alternate = false;
        
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(cardNumber.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (sum % 10) == 0;
    }
    
    /**
     * Business logic method to check if card is ready for use
     * Combines multiple validation checks
     * 
     * @return true if card is active, not expired, and has valid card number
     */
    public boolean isReadyForUse() {
        return isActive() && !isExpired() && isValidCardNumber();
    }
    
    /**
     * Business logic method to get available credit from associated account
     * Delegates to Account entity for credit calculation
     * 
     * @return available credit amount from associated account
     */
    public java.math.BigDecimal getAvailableCredit() {
        if (account != null) {
            return account.getAvailableCredit();
        }
        return java.math.BigDecimal.ZERO.setScale(2);
    }
    
    /**
     * Business logic method to get current balance from associated account
     * Delegates to Account entity for balance retrieval
     * 
     * @return current balance from associated account
     */
    public java.math.BigDecimal getCurrentBalance() {
        if (account != null) {
            return account.getCurrentBalance();
        }
        return java.math.BigDecimal.ZERO.setScale(2);
    }
    
    /**
     * Business logic method to get credit limit from associated account
     * Delegates to Account entity for credit limit retrieval
     * 
     * @return credit limit from associated account
     */
    public java.math.BigDecimal getCreditLimit() {
        if (account != null) {
            return account.getCreditLimit();
        }
        return java.math.BigDecimal.ZERO.setScale(2);
    }
    
    /**
     * Business logic method to check if associated account is active
     * Delegates to Account entity for status check
     * 
     * @return true if associated account is active
     */
    public boolean isAccountActive() {
        if (account != null) {
            return account.isActive();
        }
        return false;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return Objects.equals(cardNumber, card.cardNumber);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber);
    }
    
    @Override
    public String toString() {
        return "Card{" +
                "cardNumber='" + cardNumber + '\'' +
                ", accountId='" + accountId + '\'' +
                ", cardCvvCode='" + cardCvvCode + '\'' +
                ", embossedName='" + embossedName + '\'' +
                ", cardExpiryDate=" + cardExpiryDate +
                ", cardStatus='" + cardStatus + '\'' +
                ", cardType='" + cardType + '\'' +
                ", versionNumber=" + versionNumber +
                '}';
    }
}