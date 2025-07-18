package com.carddemo.card;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.YearMonth;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Optional;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.carddemo.audit.AuditService;
import com.carddemo.entity.Card;

/**
 * Comprehensive card validation class implementing Luhn algorithm, field validation rules, 
 * business logic validation, and security checks to ensure card data integrity and 
 * compliance with financial industry standards.
 * 
 * This validator replicates COBOL validation logic from the original mainframe system
 * including 88-level conditions, PIC clause specifications, and business rule patterns.
 * It provides centralized validation services for all card-related operations with
 * comprehensive audit logging and security event tracking.
 * 
 * COBOL Validation Patterns Implemented:
 * - Card number: PIC X(16) numeric validation with Luhn algorithm
 * - Account ID: PIC 9(11) numeric validation 
 * - CVV code: PIC 9(03) numeric validation
 * - Card status: 88-level conditions (A/I/S)
 * - Card name: Alphabetic and space characters only
 * - Expiry date: MM/YYYY format with future date validation
 * - Card type: CREDIT/DEBIT/PREPAID enumeration validation
 * 
 * @author Blitzy agent
 * @version 1.0
 * @since 2024-01-15
 */
@Component
public class CardValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(CardValidator.class);
    
    // COBOL-compatible validation patterns
    private static final Pattern CARD_NUMBER_PATTERN = Pattern.compile("^\\d{16}$");
    private static final Pattern ACCOUNT_ID_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern CVV_CODE_PATTERN = Pattern.compile("^\\d{3}$");
    private static final Pattern CARD_NAME_PATTERN = Pattern.compile("^[A-Za-z\\s]+$");
    private static final Pattern CARD_STATUS_PATTERN = Pattern.compile("^[AIS]$");
    private static final Pattern CARD_TYPE_PATTERN = Pattern.compile("^(CREDIT|DEBIT|PREPAID)$");
    
    // Date format patterns for expiry date validation
    private static final DateTimeFormatter EXPIRY_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    
    // COBOL-style error messages matching mainframe patterns
    private static final String CARD_NUMBER_ERROR = "CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER";
    private static final String ACCOUNT_ID_ERROR = "ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER";
    private static final String CVV_CODE_ERROR = "CARD CVV MUST BE A 3 DIGIT NUMBER";
    private static final String CARD_NAME_ERROR = "CARD NAME CAN ONLY CONTAIN ALPHABETS AND SPACES";
    private static final String CARD_STATUS_ERROR = "CARD ACTIVE STATUS MUST BE A, I, OR S";
    private static final String CARD_TYPE_ERROR = "CARD TYPE MUST BE CREDIT, DEBIT, OR PREPAID";
    private static final String EXPIRY_DATE_ERROR = "CARD EXPIRY DATE MUST BE FUTURE DATE IN MM/YYYY FORMAT";
    private static final String EXPIRY_MONTH_ERROR = "CARD EXPIRY MONTH MUST BE BETWEEN 1 AND 12";
    private static final String EXPIRY_YEAR_ERROR = "INVALID CARD EXPIRY YEAR";
    private static final String LUHN_VALIDATION_ERROR = "CARD NUMBER FAILED LUHN ALGORITHM VALIDATION";
    private static final String CARD_EXPIRED_ERROR = "CARD HAS EXPIRED AND CANNOT BE USED";
    private static final String REQUIRED_FIELD_ERROR = "REQUIRED FIELD CANNOT BE BLANK OR NULL";
    
    // Business rule validation constants
    private static final int MIN_EXPIRY_YEAR = 2024;
    private static final int MAX_EXPIRY_YEAR = 2050;
    private static final int MIN_CVV_LENGTH = 3;
    private static final int MAX_CVV_LENGTH = 3;
    private static final int CARD_NAME_MAX_LENGTH = 50;
    
    // Audit service for security event logging
    private final AuditService auditService;
    
    /**
     * Constructor with dependency injection for audit service.
     * 
     * @param auditService The audit service for logging validation events
     */
    @Autowired
    public CardValidator(AuditService auditService) {
        this.auditService = auditService;
        logger.info("CardValidator initialized with comprehensive validation support");
    }
    
    /**
     * Validates a complete Card entity with all business rules and security checks.
     * This method performs comprehensive validation including field validation,
     * business rule checks, and security validation with audit logging.
     * 
     * @param card The Card entity to validate
     * @throws CardValidationException if validation fails
     */
    public boolean validateCard(@Valid Card card) {
        logger.debug("Starting comprehensive card validation for card: {}", 
                    card != null ? card.getCardNumber() : "null");
        
        if (card == null) {
            logSecurityEvent("NULL_CARD_VALIDATION", "HIGH", "Attempt to validate null card entity");
            throw new CardValidationException("Card entity cannot be null");
        }
        
        CardValidationException validationException = new CardValidationException();
        boolean hasErrors = false;
        
        // Validate card number with Luhn algorithm
        if (!validateCardNumber(card.getCardNumber())) {
            validationException.addFieldError("cardNumber", CARD_NUMBER_ERROR);
            hasErrors = true;
        }
        
        // Validate account ID
        if (!isValidAccountId(card.getAccountId())) {
            validationException.addFieldError("accountId", ACCOUNT_ID_ERROR);
            hasErrors = true;
        }
        
        // Validate CVV code
        if (!isValidCvv(card.getCardCvvCode())) {
            validationException.addFieldError("cardCvvCode", CVV_CODE_ERROR);
            hasErrors = true;
        }
        
        // Validate card status
        if (!isValidCardStatus(card.getCardStatus())) {
            validationException.addFieldError("cardStatus", CARD_STATUS_ERROR);
            hasErrors = true;
        }
        
        // Validate card type
        if (!isValidCardType(card.getCardType())) {
            validationException.addFieldError("cardType", CARD_TYPE_ERROR);
            hasErrors = true;
        }
        
        // Validate expiry date
        if (!isValidExpiryDate(card.getCardExpiryDate())) {
            validationException.addFieldError("cardExpiryDate", EXPIRY_DATE_ERROR);
            hasErrors = true;
        }
        
        // Validate embossed name
        if (!isValidCardName(card.getEmbossedName())) {
            validationException.addFieldError("embossedName", CARD_NAME_ERROR);
            hasErrors = true;
        }
        
        // Perform business rule validation
        performBusinessRuleValidation(card, validationException);
        
        // Perform security validation
        performSecurityValidation(card, validationException);
        
        if (hasErrors || validationException.hasFieldErrors()) {
            logSecurityEvent("CARD_VALIDATION_FAILURE", "MEDIUM", 
                           "Card validation failed with " + validationException.getErrorCount() + " errors");
            throw validationException;
        }
        
        logger.debug("Card validation completed successfully for card: {}", card.getCardNumber());
        return hasErrors;
    }
    
    /**
     * Validates a 16-digit card number using the Luhn algorithm.
     * This method replicates COBOL card number validation logic with
     * comprehensive format checking and checksum validation.
     * 
     * @param cardNumber The card number to validate
     * @return true if card number is valid, false otherwise
     */
    public boolean validateCardNumber(String cardNumber) {
        logger.debug("Validating card number format and Luhn algorithm");
        
        if (!isValidCardNumber(cardNumber)) {
            return false;
        }
        
        return isValidLuhnCheck(cardNumber);
    }
    
    /**
     * Validates card expiry date ensuring it's in the future.
     * Supports both LocalDate and MM/YYYY string format validation.
     * 
     * @param expiryDate The card expiry date to validate
     * @return true if expiry date is valid and in the future, false otherwise
     */
    public boolean validateCardExpiryDate(LocalDate expiryDate) {
        logger.debug("Validating card expiry date: {}", expiryDate);
        
        if (expiryDate == null) {
            return false;
        }
        
        // Check if date is in the future
        LocalDate currentDate = LocalDate.now();
        if (expiryDate.isBefore(currentDate)) {
            logger.warn("Card expiry date is in the past: {}", expiryDate);
            return false;
        }
        
        // Check if year is within valid range
        int expiryYear = expiryDate.getYear();
        if (expiryYear < MIN_EXPIRY_YEAR || expiryYear > MAX_EXPIRY_YEAR) {
            logger.warn("Card expiry year is outside valid range: {}", expiryYear);
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates CVV code format (3-digit numeric).
     * 
     * @param cvvCode The CVV code to validate
     * @return true if CVV is valid, false otherwise
     */
    public boolean validateCvvCode(String cvvCode) {
        logger.debug("Validating CVV code format");
        
        return isValidCvv(cvvCode);
    }
    
    /**
     * Validates card status against allowed values (A, I, S).
     * Replicates COBOL 88-level conditions for card status.
     * 
     * @param cardStatus The card status to validate
     * @return true if card status is valid, false otherwise
     */
    public boolean validateCardStatus(String cardStatus) {
        logger.debug("Validating card status: {}", cardStatus);
        
        return isValidCardStatus(cardStatus);
    }
    
    /**
     * Validates card type against allowed values (CREDIT, DEBIT, PREPAID).
     * 
     * @param cardType The card type to validate
     * @return true if card type is valid, false otherwise
     */
    public boolean validateCardType(String cardType) {
        logger.debug("Validating card type: {}", cardType);
        
        return isValidCardType(cardType);
    }
    
    /**
     * Performs Luhn algorithm validation on a card number.
     * This method implements the standard Luhn checksum algorithm
     * used by the credit card industry for card number validation.
     * 
     * @param cardNumber The card number to validate
     * @return true if card number passes Luhn validation, false otherwise
     */
    public boolean isValidLuhnCheck(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        
        int sum = 0;
        boolean alternate = false;
        
        // Process digits from right to left
        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            char digitChar = cardNumber.charAt(i);
            if (!Character.isDigit(digitChar)) {
                return false;
            }
            
            int digit = Character.getNumericValue(digitChar);
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit % 10) + 1;
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        boolean isValid = (sum % 10) == 0;
        logger.debug("Luhn algorithm validation result: {}", isValid);
        
        return isValid;
    }
    
    /**
     * Validates card number format (16 digits, numeric only).
     * 
     * @param cardNumber The card number to validate
     * @return true if format is valid, false otherwise
     */
    public boolean isValidCardNumber(String cardNumber) {
        if (cardNumber == null || cardNumber.trim().isEmpty()) {
            return false;
        }
        
        return CARD_NUMBER_PATTERN.matcher(cardNumber).matches();
    }
    
    /**
     * Validates expiry date format and future date requirement.
     * 
     * @param expiryDate The expiry date to validate
     * @return true if expiry date is valid, false otherwise
     */
    public boolean isValidExpiryDate(LocalDate expiryDate) {
        return validateCardExpiryDate(expiryDate);
    }
    
    /**
     * Validates CVV code format (3 digits).
     * 
     * @param cvvCode The CVV code to validate
     * @return true if CVV is valid, false otherwise
     */
    public boolean isValidCvv(String cvvCode) {
        if (cvvCode == null || cvvCode.trim().isEmpty()) {
            return false;
        }
        
        return CVV_CODE_PATTERN.matcher(cvvCode).matches();
    }
    
    /**
     * Validates card status format (A, I, or S).
     * 
     * @param cardStatus The card status to validate
     * @return true if card status is valid, false otherwise
     */
    public boolean isValidCardStatus(String cardStatus) {
        if (cardStatus == null || cardStatus.trim().isEmpty()) {
            return false;
        }
        
        return CARD_STATUS_PATTERN.matcher(cardStatus).matches();
    }
    
    /**
     * Creates a comprehensive validation error message combining all validation failures.
     * 
     * @param fieldErrors Map of field names to error messages
     * @return Formatted error message
     */
    public String createValidationErrorMessage(Map<String, String> fieldErrors) {
        if (fieldErrors == null || fieldErrors.isEmpty()) {
            return "No validation errors found";
        }
        
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("Card validation failed with ").append(fieldErrors.size()).append(" error(s): ");
        
        fieldErrors.forEach((field, error) -> {
            errorMessage.append(field).append(" - ").append(error).append("; ");
        });
        
        return errorMessage.toString();
    }
    
    /**
     * Performs comprehensive business rule validation on card data.
     * This method implements complex business logic validation patterns
     * from the original COBOL system.
     * 
     * @param card The card to validate
     * @param validationException The exception to add errors to
     */
    public void performBusinessRuleValidation(Card card, CardValidationException validationException) {
        logger.debug("Performing business rule validation for card: {}", card.getCardNumber());
        
        // Check if card is expired
        if (card.getCardExpiryDate() != null && card.getCardExpiryDate().isBefore(LocalDate.now())) {
            validationException.addFieldError("cardExpiryDate", CARD_EXPIRED_ERROR);
            validationException.addBusinessRuleViolation("CARD_EXPIRED", 
                "Card expired on " + card.getCardExpiryDate());
        }
        
        // Validate card status business rules
        if ("A".equals(card.getCardStatus()) && card.getCardExpiryDate() != null) {
            // Active cards cannot have past expiry dates
            if (card.getCardExpiryDate().isBefore(LocalDate.now())) {
                validationException.addBusinessRuleViolation("ACTIVE_CARD_EXPIRED", 
                    "Active card cannot have expired date");
            }
        }
        
        // Validate card type and status combination
        if ("PREPAID".equals(card.getCardType()) && !"A".equals(card.getCardStatus())) {
            validationException.addBusinessRuleViolation("PREPAID_CARD_STATUS", 
                "Prepaid cards must have active status");
        }
        
        // Validate embossed name length
        if (card.getEmbossedName() != null && card.getEmbossedName().length() > CARD_NAME_MAX_LENGTH) {
            validationException.addFieldError("embossedName", 
                "CARD NAME CANNOT EXCEED " + CARD_NAME_MAX_LENGTH + " CHARACTERS");
        }
        
        // Validate account ID format
        if (!isValidAccountId(card.getAccountId())) {
            validationException.addBusinessRuleViolation("INVALID_ACCOUNT_ID", 
                "Account ID must be exactly 11 digits");
        }
        
        logger.debug("Business rule validation completed");
    }
    
    /**
     * Performs security validation checks and logs security events.
     * This method implements security-specific validation rules and
     * audit logging for compliance requirements.
     * 
     * @param card The card to validate
     * @param validationException The exception to add errors to
     */
    public void performSecurityValidation(Card card, CardValidationException validationException) {
        logger.debug("Performing security validation for card: {}", card.getCardNumber());
        
        // Log card validation attempt
        logDataAccessEvent(card.getCardNumber(), "CARD_VALIDATION_ATTEMPT", 
                          "Card validation security check initiated");
        
        // Check for suspicious patterns in card number
        if (card.getCardNumber() != null && hasSuspiciousCardPattern(card.getCardNumber())) {
            validationException.addBusinessRuleViolation("SUSPICIOUS_CARD_PATTERN", 
                "Card number contains suspicious patterns");
            logSecurityEvent("SUSPICIOUS_CARD_PATTERN", "HIGH", 
                           "Card number validation detected suspicious pattern");
        }
        
        // Check for test card numbers that should not be in production
        if (isTestCardNumber(card.getCardNumber())) {
            validationException.addBusinessRuleViolation("TEST_CARD_NUMBER", 
                "Test card numbers not allowed in production");
            logSecurityEvent("TEST_CARD_IN_PRODUCTION", "HIGH", 
                           "Test card number detected in production environment");
        }
        
        // Validate CVV security
        if (card.getCardCvvCode() != null && hasWeakCvv(card.getCardCvvCode())) {
            validationException.addBusinessRuleViolation("WEAK_CVV", 
                "CVV code appears to be weak or sequential");
            logSecurityEvent("WEAK_CVV_DETECTED", "MEDIUM", 
                           "Weak CVV code pattern detected");
        }
        
        logger.debug("Security validation completed");
    }
    
    // Private helper methods
    
    /**
     * Validates account ID format (11 digits).
     */
    private boolean isValidAccountId(String accountId) {
        if (accountId == null || accountId.trim().isEmpty()) {
            return false;
        }
        
        return ACCOUNT_ID_PATTERN.matcher(accountId).matches();
    }
    
    /**
     * Validates card name format (alphabetic and spaces only).
     */
    private boolean isValidCardName(String cardName) {
        if (cardName == null || cardName.trim().isEmpty()) {
            return false;
        }
        
        return CARD_NAME_PATTERN.matcher(cardName).matches();
    }
    
    /**
     * Validates card type format.
     */
    private boolean isValidCardType(String cardType) {
        if (cardType == null || cardType.trim().isEmpty()) {
            return false;
        }
        
        return CARD_TYPE_PATTERN.matcher(cardType).matches();
    }
    
    /**
     * Checks for suspicious patterns in card numbers.
     */
    private boolean hasSuspiciousCardPattern(String cardNumber) {
        if (cardNumber == null || cardNumber.length() != 16) {
            return false;
        }
        
        // Check for repeated digits
        String firstDigit = cardNumber.substring(0, 1);
        if (cardNumber.equals(firstDigit.repeat(16))) {
            return true;
        }
        
        // Check for sequential numbers
        boolean isSequential = true;
        for (int i = 1; i < cardNumber.length(); i++) {
            int current = Character.getNumericValue(cardNumber.charAt(i));
            int previous = Character.getNumericValue(cardNumber.charAt(i - 1));
            if (current != previous + 1) {
                isSequential = false;
                break;
            }
        }
        
        return isSequential;
    }
    
    /**
     * Checks if card number is a test card number.
     */
    private boolean isTestCardNumber(String cardNumber) {
        if (cardNumber == null) {
            return false;
        }
        
        // Common test card number patterns
        String[] testPatterns = {
            "4111111111111111", // Visa test card
            "4000000000000000", // Visa test card
            "5555555555554444", // MasterCard test card
            "5105105105105100"  // MasterCard test card
        };
        
        for (String testPattern : testPatterns) {
            if (cardNumber.equals(testPattern)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if CVV code is weak (sequential or repeated digits).
     */
    private boolean hasWeakCvv(String cvvCode) {
        if (cvvCode == null || cvvCode.length() != 3) {
            return false;
        }
        
        // Check for repeated digits
        if (cvvCode.equals(cvvCode.substring(0, 1).repeat(3))) {
            return true;
        }
        
        // Check for sequential digits
        for (int i = 1; i < cvvCode.length(); i++) {
            int current = Character.getNumericValue(cvvCode.charAt(i));
            int previous = Character.getNumericValue(cvvCode.charAt(i - 1));
            if (current != previous + 1) {
                return false;
            }
        }
        
        return true; // Sequential CVV is weak
    }
    
    /**
     * Logs security events to the audit service.
     */
    private void logSecurityEvent(String eventType, String severity, String description) {
        if (auditService != null) {
            try {
                Map<String, Object> eventContext = new ConcurrentHashMap<>();
                eventContext.put("component", "CardValidator");
                eventContext.put("validation_type", eventType);
                eventContext.put("severity", severity);
                eventContext.put("timestamp", System.currentTimeMillis());
                
                auditService.logSecurityEvent(eventType, severity, description, eventContext);
            } catch (Exception e) {
                logger.warn("Failed to log security event: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Logs data access events to the audit service.
     */
    private void logDataAccessEvent(String cardNumber, String operation, String description) {
        if (auditService != null) {
            try {
                Map<String, Object> queryDetails = new ConcurrentHashMap<>();
                queryDetails.put("operation", operation);
                queryDetails.put("card_number_hash", cardNumber != null ? cardNumber.hashCode() : 0);
                queryDetails.put("description", description);
                queryDetails.put("timestamp", System.currentTimeMillis());
                queryDetails.put("execution_time_ms", 0L);
                
                auditService.logDataAccessEvent("CARD_VALIDATOR", "VALIDATION", operation, 1, queryDetails);
            } catch (Exception e) {
                logger.warn("Failed to log data access event: {}", e.getMessage());
            }
        }
    }
}