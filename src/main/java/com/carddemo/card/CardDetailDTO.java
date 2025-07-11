package com.carddemo.card;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.Objects;
import java.math.BigDecimal;

import com.carddemo.account.Account;

/**
 * Data Transfer Object for comprehensive card detail operations.
 * 
 * This DTO provides comprehensive card information, related entity data,
 * field-level security, and audit trail support for detailed card view
 * functionality. It maintains exact BMS field layouts while implementing
 * modern security patterns and audit compliance.
 * 
 * Original COBOL Program: COCRDSLC.cbl - Card Detail/Selection Logic
 * Original Data Structure: CVCRD01Y.cpy - Card working area copybook
 * 
 * Key Features:
 * - Comprehensive card detail information transfer preserving BMS field layouts
 * - Related entity data inclusion for complete card view functionality
 * - Field-level security annotations for sensitive data protection (CVV codes)
 * - Audit trail information for compliance and security tracking requirements
 * 
 * Security Implementation:
 * - CVV codes are marked with @JsonIgnore to prevent JSON serialization
 * - Sensitive data masking through dedicated utility methods
 * - Audit trail fields for compliance and security tracking
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-01
 */
public class CardDetailDTO {
    
    // Core Card Information Fields
    // Maps to COBOL CC-CARD-NUM PIC X(16) field
    @NotBlank(message = "Card number cannot be blank")
    private String cardNumber;
    
    // Maps to COBOL CC-ACCT-ID PIC X(11) field
    @NotBlank(message = "Account ID cannot be blank")
    private String accountId;
    
    // Maps to COBOL CARD-CVV-CD PIC 9(03) field - SENSITIVE DATA
    @JsonIgnore // Prevents CVV from being serialized to JSON for security
    private String cardCvvCode;
    
    // Maps to COBOL CARD-EXPIRAION-DATE PIC X(10) field
    private LocalDate cardExpiryDate;
    
    // Maps to COBOL CARD-ACTIVE-STATUS PIC X(01) field
    private String cardStatus;
    
    // Maps to COBOL CARD-EMBOSSED-NAME PIC X(50) field
    private String cardType;
    
    // Maps to COBOL CARD-EMBOSSED-NAME PIC X(50) field
    private String embossedName;
    
    // Related Account Information Fields
    // Provides comprehensive account context for card detail display
    private BigDecimal accountBalance;
    private BigDecimal accountCreditLimit;
    private String accountStatus;
    private LocalDate accountOpenDate;
    
    // Audit Trail Fields
    // Supports compliance and security tracking requirements
    private LocalDate lastAccessedDate;
    private String lastModifiedBy;
    private String auditTrailId;
    
    // Security and Utility Fields
    private boolean cvvMasked;
    
    /**
     * Default constructor for DTO instantiation.
     */
    public CardDetailDTO() {
        // Initialize default values for safety
        this.accountBalance = BigDecimal.ZERO.setScale(2);
        this.accountCreditLimit = BigDecimal.ZERO.setScale(2);
        this.cvvMasked = true; // Default to masked for security
    }
    
    /**
     * Constructor with Card and Account entities for comprehensive detail display.
     * 
     * This constructor creates a complete card detail DTO by combining card
     * information with related account data, supporting the card-account
     * relationship display requirements from the BMS screen layouts.
     * 
     * @param card Card entity containing primary card information
     * @param account Account entity containing related account information
     */
    public CardDetailDTO(Card card, Account account) {
        this();
        
        if (card != null) {
            this.cardNumber = card.getCardNumber();
            this.accountId = card.getAccountId();
            this.cardCvvCode = card.getCardCvvCode();
            this.cardExpiryDate = card.getCardExpiryDate();
            this.cardStatus = card.getCardStatus();
            this.cardType = card.getCardType();
            this.embossedName = card.getEmbossedName();
        }
        
        if (account != null) {
            this.accountBalance = account.getCurrentBalance();
            this.accountCreditLimit = account.getCreditLimit();
            this.accountStatus = account.getActiveStatus();
            this.accountOpenDate = account.getOpenDate();
        }
        
        // Set audit trail information
        this.lastAccessedDate = LocalDate.now();
        this.auditTrailId = generateAuditTrailId();
    }
    
    // Getter and Setter Methods
    
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
    
    /**
     * Returns the card CVV code.
     * Note: This method is available for internal processing but the field
     * is marked with @JsonIgnore to prevent JSON serialization.
     * 
     * @return card CVV code for internal processing
     */
    public String getCardCvvCode() {
        return cardCvvCode;
    }
    
    public void setCardCvvCode(String cardCvvCode) {
        this.cardCvvCode = cardCvvCode;
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
    
    public String getEmbossedName() {
        return embossedName;
    }
    
    public void setEmbossedName(String embossedName) {
        this.embossedName = embossedName;
    }
    
    public BigDecimal getAccountBalance() {
        return accountBalance;
    }
    
    public void setAccountBalance(BigDecimal accountBalance) {
        this.accountBalance = accountBalance != null ? 
            accountBalance.setScale(2, java.math.RoundingMode.HALF_UP) : 
            BigDecimal.ZERO.setScale(2);
    }
    
    public BigDecimal getAccountCreditLimit() {
        return accountCreditLimit;
    }
    
    public void setAccountCreditLimit(BigDecimal accountCreditLimit) {
        this.accountCreditLimit = accountCreditLimit != null ? 
            accountCreditLimit.setScale(2, java.math.RoundingMode.HALF_UP) : 
            BigDecimal.ZERO.setScale(2);
    }
    
    public String getAccountStatus() {
        return accountStatus;
    }
    
    public void setAccountStatus(String accountStatus) {
        this.accountStatus = accountStatus;
    }
    
    public LocalDate getAccountOpenDate() {
        return accountOpenDate;
    }
    
    public void setAccountOpenDate(LocalDate accountOpenDate) {
        this.accountOpenDate = accountOpenDate;
    }
    
    public LocalDate getLastAccessedDate() {
        return lastAccessedDate;
    }
    
    public void setLastAccessedDate(LocalDate lastAccessedDate) {
        this.lastAccessedDate = lastAccessedDate;
    }
    
    public String getLastModifiedBy() {
        return lastModifiedBy;
    }
    
    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }
    
    public String getAuditTrailId() {
        return auditTrailId;
    }
    
    public void setAuditTrailId(String auditTrailId) {
        this.auditTrailId = auditTrailId;
    }
    
    // Business Logic and Utility Methods
    
    /**
     * Checks if the card is expired based on the expiry date.
     * Replicates COBOL date comparison logic from COCRDSLC program.
     * 
     * @return true if card is expired, false otherwise
     */
    public boolean isCardExpired() {
        if (cardExpiryDate == null) {
            return true; // Consider null expiry date as expired for safety
        }
        return cardExpiryDate.isBefore(LocalDate.now());
    }
    
    /**
     * Checks if CVV is currently masked for security purposes.
     * 
     * @return true if CVV is masked, false otherwise
     */
    public boolean isCvvMasked() {
        return cvvMasked;
    }
    
    /**
     * Masks sensitive card data for security compliance.
     * This method sets the CVV masked flag and clears sensitive data
     * from the DTO to prevent accidental exposure.
     * 
     * Implementation follows field-level security requirements from
     * the technical specification section 6.4 Security Architecture.
     */
    public void maskSensitiveData() {
        this.cvvMasked = true;
        this.cardCvvCode = null; // Clear CVV for security
    }
    
    /**
     * Generates a unique audit trail identifier for compliance tracking.
     * 
     * @return unique audit trail identifier
     */
    private String generateAuditTrailId() {
        return "CDT-" + System.currentTimeMillis() + "-" + 
               (cardNumber != null ? cardNumber.substring(cardNumber.length() - 4) : "0000");
    }
    
    /**
     * Validates if the card detail information is complete and valid.
     * Replicates COBOL validation logic from COCRDSLC program.
     * 
     * @return true if card detail is valid, false otherwise
     */
    public boolean isValid() {
        return cardNumber != null && !cardNumber.trim().isEmpty() &&
               accountId != null && !accountId.trim().isEmpty() &&
               cardExpiryDate != null &&
               cardStatus != null && !cardStatus.trim().isEmpty() &&
               cardType != null && !cardType.trim().isEmpty();
    }
    
    /**
     * Calculates available credit based on account balance and credit limit.
     * Maintains COBOL-style decimal arithmetic precision.
     * 
     * @return available credit amount
     */
    public BigDecimal getAvailableCredit() {
        if (accountCreditLimit == null || accountBalance == null) {
            return BigDecimal.ZERO.setScale(2);
        }
        return accountCreditLimit.subtract(accountBalance).setScale(2, java.math.RoundingMode.HALF_UP);
    }
    
    /**
     * Checks if the associated account is active.
     * Replicates COBOL 88-level condition logic.
     * 
     * @return true if account is active, false otherwise
     */
    public boolean isAccountActive() {
        return "A".equals(accountStatus);
    }
    
    /**
     * Checks if the card is active.
     * Replicates COBOL 88-level condition logic.
     * 
     * @return true if card is active, false otherwise
     */
    public boolean isCardActive() {
        return "A".equals(cardStatus);
    }
    
    /**
     * Formats card number for display with masking.
     * Shows only last 4 digits for security.
     * 
     * @return masked card number for display
     */
    public String getFormattedCardNumber() {
        if (cardNumber == null || cardNumber.length() < 4) {
            return "****-****-****-****";
        }
        return "****-****-****-" + cardNumber.substring(cardNumber.length() - 4);
    }
    
    /**
     * Formats expiry date for display in MM/YY format.
     * Maintains compatibility with BMS screen layouts.
     * 
     * @return formatted expiry date string
     */
    public String getFormattedExpiryDate() {
        if (cardExpiryDate == null) {
            return "**/**";
        }
        return String.format("%02d/%02d", 
            cardExpiryDate.getMonthValue(), 
            cardExpiryDate.getYear() % 100);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardDetailDTO that = (CardDetailDTO) o;
        return Objects.equals(cardNumber, that.cardNumber) &&
               Objects.equals(accountId, that.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber, accountId);
    }
    
    @Override
    public String toString() {
        return "CardDetailDTO{" +
                "cardNumber='" + getFormattedCardNumber() + '\'' +
                ", accountId='" + accountId + '\'' +
                ", cardExpiryDate=" + cardExpiryDate +
                ", cardStatus='" + cardStatus + '\'' +
                ", cardType='" + cardType + '\'' +
                ", embossedName='" + embossedName + '\'' +
                ", accountBalance=" + accountBalance +
                ", accountCreditLimit=" + accountCreditLimit +
                ", accountStatus='" + accountStatus + '\'' +
                ", accountOpenDate=" + accountOpenDate +
                ", lastAccessedDate=" + lastAccessedDate +
                ", lastModifiedBy='" + lastModifiedBy + '\'' +
                ", auditTrailId='" + auditTrailId + '\'' +
                ", cvvMasked=" + cvvMasked +
                '}';
    }

	public Integer getVersionNumber() {
		return null;
	}
}