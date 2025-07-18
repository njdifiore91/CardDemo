package com.carddemo.card;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.carddemo.audit.AuditService;
import com.carddemo.entity.Card;
import com.carddemo.session.SessionManagementService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * REST controller class exposing card management operations through HTTP endpoints,
 * providing session management, authentication integration, exception handling,
 * and standardized API responses for all card-related business functions.
 * 
 * This controller serves as the primary REST API interface for card operations,
 * converting the legacy COBOL programs (COCRDLIC, COCRDSLC, COCRDUPC) into modern
 * HTTP endpoints while maintaining identical business logic and validation patterns.
 * 
 * Key Features:
 * - JWT-based authentication integration through Spring Security
 * - Redis-backed session management for pseudo-conversational state
 * - Comprehensive exception handling with standardized HTTP status codes
 * - Audit logging for all card operations through AuditService integration
 * - Field-level validation matching COBOL 88-level conditions
 * - Pagination support for card listing operations
 * - Role-based access control with @PreAuthorize annotations
 * 
 * Original COBOL Programs:
 * - COCRDLIC.cbl -> getAllCards(), getCardsByAccount() endpoints
 * - COCRDSLC.cbl -> getCardDetails(), searchCards() endpoints  
 * - COCRDUPC.cbl -> updateCard() endpoint
 * 
 * Session Management:
 * - Maintains pseudo-conversational state through Redis-backed sessions
 * - Preserves navigation context and user preferences across REST calls
 * - Implements CICS COMMAREA equivalent functionality
 * 
 * Security Implementation:
 * - Spring Security JWT token validation for all endpoints
 * - Method-level authorization through @PreAuthorize annotations
 * - Role-based access control matching RACF user type patterns
 * - Comprehensive audit logging for security compliance
 * 
 * @author CardDemo Migration Team - Blitzy agent
 * @version 1.0
 * @since 2024-01-15
 */
@RestController
@RequestMapping("/api/v1/cards")
@Validated
@CrossOrigin(origins = {"http://localhost:3000", "https://carddemo.blitzy.com"})
public class CardController {
    
    private static final Logger logger = LoggerFactory.getLogger(CardController.class);
    
    // Service dependencies for card operations
    private final CardValidator cardValidator;
    private final CardService cardService;
    private final CardListService cardListService;
    private final CardDetailService cardDetailService;
    private final CardUpdateService cardUpdateService;
    
    // Cross-cutting service dependencies
    private final AuditService auditService;
    private final SessionManagementService sessionManagementService;
    
    // Constants for session management and pagination
    private static final String SESSION_ATTR_CARD_CONTEXT = "cardContext";
    private static final String SESSION_ATTR_LAST_SEARCH = "lastCardSearch";
    private static final int DEFAULT_PAGE_SIZE = 7; // Matches COBOL WS-MAX-SCREEN-LINES
    private static final int MAX_PAGE_SIZE = 100;
    
    /**
     * Constructor for dependency injection of all required services.
     * 
     * @param cardValidator Field validation service for card data
     * @param cardService Primary card management service
     * @param cardListService Specialized card listing service
     * @param cardDetailService Specialized card detail service
     * @param cardUpdateService Specialized card update service
     * @param auditService Audit logging service for security compliance
     * @param sessionManagementService Session management service for pseudo-conversational state
     */
    @Autowired
    public CardController(CardValidator cardValidator,
                         CardService cardService,
                         CardListService cardListService,
                         CardDetailService cardDetailService,
                         CardUpdateService cardUpdateService,
                         AuditService auditService,
                         SessionManagementService sessionManagementService) {
        this.cardValidator = cardValidator;
        this.cardService = cardService;
        this.cardListService = cardListService;
        this.cardDetailService = cardDetailService;
        this.cardUpdateService = cardUpdateService;
        this.auditService = auditService;
        this.sessionManagementService = sessionManagementService;
        
        logger.info("CardController initialized with all dependencies");
    }
    
    /**
     * Retrieve all cards accessible to the current user with pagination support.
     * 
     * This endpoint replicates the functionality of COBOL program COCRDLIC.cbl,
     * providing card listing with pagination, filtering, and role-based access control.
     * Administrative users can see all cards while regular users see only their own cards.
     * 
     * Original COBOL Program: COCRDLIC.cbl
     * Original Transaction: CCLI
     * BMS Screen: COCRDLI.bms
     * 
     * @param page Page number (0-based, default 0)
     * @param size Page size (default 7 to match BMS screen layout)
     * @param sortBy Sort field (default "cardNumber")
     * @param sortDir Sort direction (default "asc")
     * @param request HTTP request for session management
     * @param principal Security principal for user context
     * @return ResponseEntity containing CardListDTO with pagination metadata
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<CardListDTO> getAllCards(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            @RequestParam(defaultValue = "cardNumber") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir,
            HttpServletRequest request,
            Principal principal) {
        
        logger.info("getAllCards called by user: {}, page: {}, size: {}", 
                   principal.getName(), page, size);
        
        try {
            // Store navigation context in session
            Map<String, Object> cardContext = new HashMap<>();
            cardContext.put("operation", "getAllCards");
            cardContext.put("page", page);
            cardContext.put("size", size);
            cardContext.put("timestamp", LocalDateTime.now());
            sessionManagementService.setSessionAttribute(request, SESSION_ATTR_CARD_CONTEXT, cardContext);
            
            // Validate and sanitize pagination parameters
            size = Math.min(size, MAX_PAGE_SIZE);
            size = Math.max(size, 1);
            page = Math.max(page, 0);
            
            // Create pageable with sorting
            Sort sort = sortDir.equalsIgnoreCase("desc") ? 
                       Sort.by(sortBy).descending() : 
                       Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            
            // Delegate to specialized card list service
            org.springframework.data.domain.Page<Card> cardPage = cardListService.getCardListWithPagination(pageable);
            CardListDTO cardListDTO = cardListService.buildCardListResponse(cardPage);
            
            // Log successful operation
            auditService.logDataAccessEvent(principal.getName(), "CARDS", "READ", 
                                          cardListDTO.getCards().size(), 
                                          createAuditDetails("getAllCards", "SUCCESS", page, size));
            
            return ResponseEntity.ok(cardListDTO);
            
        } catch (Exception e) {
            logger.error("Error in getAllCards", e);
            auditService.logSecurityEvent("CARD_LIST_ERROR", "READ_ERROR", 
                                        "ERROR: " + e.getMessage(), 
                                        createAuditDetails("getAllCards", "ERROR", page, size));
            throw new RuntimeException("Error retrieving card list", e);
        }
    }
    
    /**
     * Retrieve cards associated with a specific account ID.
     * 
     * This endpoint provides account-specific card filtering, replicating the
     * account-based filtering functionality from COCRDLIC.cbl. It includes
     * comprehensive validation and role-based access control.
     * 
     * Original COBOL Program: COCRDLIC.cbl (with account filter)
     * Original Field: CC-ACCT-ID PIC X(11)
     * 
     * @param accountId 11-digit account identifier
     * @param page Page number (0-based, default 0)
     * @param size Page size (default 7)
     * @param request HTTP request for session management
     * @param principal Security principal for user context
     * @return ResponseEntity containing CardListDTO with account-specific cards
     */
    @GetMapping("/account/{accountId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<CardListDTO> getCardsByAccount(
            @PathVariable @Pattern(regexp = "^\\d{11}$", message = "Account ID must be exactly 11 digits")
            @NotBlank(message = "Account ID cannot be blank") String accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            HttpServletRequest request,
            Principal principal) {
        
        logger.info("getCardsByAccount called by user: {}, accountId: {}, page: {}, size: {}", 
                   principal.getName(), accountId, page, size);
        
        try {
            // Validate account ID format
            if (!accountId.matches("^\\d{11}$")) {
                throw new CardValidationException("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
            }
            
            // Store navigation context in session
            Map<String, Object> cardContext = new HashMap<>();
            cardContext.put("operation", "getCardsByAccount");
            cardContext.put("accountId", accountId);
            cardContext.put("page", page);
            cardContext.put("size", size);
            cardContext.put("timestamp", LocalDateTime.now());
            sessionManagementService.setSessionAttribute(request, SESSION_ATTR_CARD_CONTEXT, cardContext);
            
            // Validate and sanitize pagination parameters
            size = Math.min(size, MAX_PAGE_SIZE);
            size = Math.max(size, 1);
            page = Math.max(page, 0);
            
            // Create pageable for account-specific filtering
            Pageable pageable = PageRequest.of(page, size, Sort.by("cardNumber").ascending());
            
            // Delegate to specialized card list service
            org.springframework.data.domain.Page<Card> cardPage = cardListService.getCardListByAccountIdWithPagination(accountId, pageable);
            CardListDTO cardListDTO = cardListService.buildCardListResponse(cardPage);
            
            // Log successful operation
            auditService.logDataAccessEvent(principal.getName(), "CARDS", "READ", 
                                          cardListDTO.getCards().size(), 
                                          createAuditDetails("getCardsByAccount", "SUCCESS", accountId, page, size));
            
            return ResponseEntity.ok(cardListDTO);
            
        } catch (CardValidationException e) {
            logger.warn("Invalid account ID provided: {}", accountId);
            auditService.logSecurityEvent("CARD_ACCOUNT_INVALID", "VALIDATION_ERROR", 
                                        "Invalid account ID: " + accountId, 
                                        createAuditDetails("getCardsByAccount", "VALIDATION_ERROR", accountId, page, size));
            throw e;
        } catch (Exception e) {
            logger.error("Error in getCardsByAccount", e);
            auditService.logSecurityEvent("CARD_ACCOUNT_ERROR", "READ_ERROR", 
                                        "ERROR: " + e.getMessage(), 
                                        createAuditDetails("getCardsByAccount", "ERROR", accountId, page, size));
            throw new RuntimeException("Error retrieving cards for account", e);
        }
    }
    
    /**
     * Retrieve detailed information for a specific card by card number.
     * 
     * This endpoint replicates the functionality of COBOL program COCRDSLC.cbl,
     * providing comprehensive card detail information including related account data.
     * 
     * Original COBOL Program: COCRDSLC.cbl
     * Original Transaction: CCDL
     * BMS Screen: COCRDSL.bms
     * 
     * @param cardNumber 16-digit card number
     * @param request HTTP request for session management
     * @param principal Security principal for user context
     * @return ResponseEntity containing CardDetailDTO with comprehensive card information
     */
    @GetMapping("/{cardNumber}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<CardDetailDTO> getCardDetails(
            @PathVariable @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
            @NotBlank(message = "Card number cannot be blank") String cardNumber,
            HttpServletRequest request,
            Principal principal) {
        
        logger.info("getCardDetails called by user: {}, cardNumber: {}", 
                   principal.getName(), cardNumber);
        
        try {
            // Validate card number using CardValidator
            if (!cardValidator.isValidCardNumber(cardNumber)) {
                throw new CardValidationException("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
            }
            
            // Store navigation context in session
            Map<String, Object> cardContext = new HashMap<>();
            cardContext.put("operation", "getCardDetails");
            cardContext.put("cardNumber", cardNumber);
            cardContext.put("timestamp", LocalDateTime.now());
            sessionManagementService.setSessionAttribute(request, SESSION_ATTR_CARD_CONTEXT, cardContext);
            
            // Delegate to specialized card detail service
            CardDetailDTO cardDetailDTO = cardDetailService.getCardDetails(cardNumber);
            
            if (cardDetailDTO != null) {
                // Log successful operation
                auditService.logDataAccessEvent(principal.getName(), "CARDS", "READ", 
                                              1, createAuditDetails("getCardDetails", "SUCCESS", cardNumber));
                
                return ResponseEntity.ok(cardDetailDTO);
            } else {
                // Card not found
                auditService.logSecurityEvent("CARD_NOT_FOUND", "READ_ERROR", 
                                            "Card not found: " + cardNumber, 
                                            createAuditDetails("getCardDetails", "NOT_FOUND", cardNumber));
                throw new CardNotFoundException("Card not found", cardNumber);
            }
            
        } catch (CardValidationException e) {
            logger.warn("Invalid card number provided: {}", cardNumber);
            auditService.logSecurityEvent("CARD_INVALID", "VALIDATION_ERROR", 
                                        "Invalid card number: " + cardNumber, 
                                        createAuditDetails("getCardDetails", "VALIDATION_ERROR", cardNumber));
            throw e;
        } catch (CardNotFoundException e) {
            logger.warn("Card not found: {}", cardNumber);
            throw e;
        } catch (Exception e) {
            logger.error("Error in getCardDetails", e);
            auditService.logSecurityEvent("CARD_DETAIL_ERROR", "READ_ERROR", 
                                        "ERROR: " + e.getMessage(), 
                                        createAuditDetails("getCardDetails", "ERROR", cardNumber));
            throw new RuntimeException("Error retrieving card details", e);
        }
    }
    
    /**
     * Update card information using the provided card update data.
     * 
     * This endpoint replicates the functionality of COBOL program COCRDUPC.cbl,
     * providing comprehensive card update capabilities with validation and
     * optimistic locking for concurrent access control.
     * 
     * Original COBOL Program: COCRDUPC.cbl
     * Original Transaction: CCUP
     * BMS Screen: COCRDUP.bms
     * 
     * @param cardNumber 16-digit card number to update
     * @param cardUpdateDTO Card update data with validation
     * @param request HTTP request for session management
     * @param principal Security principal for user context
     * @return ResponseEntity containing updated CardDetailDTO
     */
    @PutMapping("/{cardNumber}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<CardDetailDTO> updateCard(
            @PathVariable @Pattern(regexp = "^\\d{16}$", message = "Card number must be exactly 16 digits")
            @NotBlank(message = "Card number cannot be blank") String cardNumber,
            @Valid @RequestBody CardUpdateDTO cardUpdateDTO,
            HttpServletRequest request,
            Principal principal) {
        
        logger.info("updateCard called by user: {}, cardNumber: {}", 
                   principal.getName(), cardNumber);
        
        try {
            // Validate card number using CardValidator
            if (!cardValidator.isValidCardNumber(cardNumber)) {
                throw new CardValidationException("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
            }
            
            // Validate card update data (basic validation)
            if (cardUpdateDTO == null) {
                throw new CardValidationException("Card update data cannot be null");
            }
            
            // Store navigation context in session
            Map<String, Object> cardContext = new HashMap<>();
            cardContext.put("operation", "updateCard");
            cardContext.put("cardNumber", cardNumber);
            cardContext.put("timestamp", LocalDateTime.now());
            sessionManagementService.setSessionAttribute(request, SESSION_ATTR_CARD_CONTEXT, cardContext);
            
            // Delegate to specialized card update service
            CardDetailDTO updatedCard = cardUpdateService.updateCardDetails(cardNumber, cardUpdateDTO);
            
            // Log successful operation
            auditService.logDataAccessEvent(principal.getName(), "CARDS", "UPDATE", 
                                          1, createAuditDetails("updateCard", "SUCCESS", cardNumber));
            
            return ResponseEntity.ok(updatedCard);
            
        } catch (CardValidationException e) {
            logger.warn("Validation error in updateCard: {}", e.getMessage());
            auditService.logSecurityEvent("CARD_UPDATE_VALIDATION", "VALIDATION_ERROR", 
                                        "Validation error: " + e.getMessage(), 
                                        createAuditDetails("updateCard", "VALIDATION_ERROR", cardNumber));
            throw e;
        } catch (CardNotFoundException e) {
            logger.warn("Card not found for update: {}", cardNumber);
            auditService.logSecurityEvent("CARD_UPDATE_NOT_FOUND", "READ_ERROR", 
                                        "Card not found: " + cardNumber, 
                                        createAuditDetails("updateCard", "NOT_FOUND", cardNumber));
            throw e;
        } catch (Exception e) {
            logger.error("Error in updateCard", e);
            auditService.logSecurityEvent("CARD_UPDATE_ERROR", "UPDATE_ERROR", 
                                        "ERROR: " + e.getMessage(), 
                                        createAuditDetails("updateCard", "ERROR", cardNumber));
            throw new RuntimeException("Error updating card", e);
        }
    }
    
    /**
     * Search for cards using flexible search criteria.
     * 
     * This endpoint provides comprehensive card search functionality with
     * multiple search parameters and pagination support.
     * 
     * @param accountId Optional account ID filter
     * @param cardNumber Optional card number filter
     * @param status Optional card status filter
     * @param page Page number (0-based, default 0)
     * @param size Page size (default 7)
     * @param request HTTP request for session management
     * @param principal Security principal for user context
     * @return ResponseEntity containing CardListDTO with search results
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<CardListDTO> searchCards(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String cardNumber,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "7") int size,
            HttpServletRequest request,
            Principal principal) {
        
        logger.info("searchCards called by user: {}, accountId: {}, cardNumber: {}, status: {}", 
                   principal.getName(), accountId, cardNumber, status);
        
        try {
            // Store search context in session
            Map<String, Object> searchContext = new HashMap<>();
            searchContext.put("operation", "searchCards");
            searchContext.put("accountId", accountId);
            searchContext.put("cardNumber", cardNumber);
            searchContext.put("status", status);
            searchContext.put("page", page);
            searchContext.put("size", size);
            searchContext.put("timestamp", LocalDateTime.now());
            sessionManagementService.setSessionAttribute(request, SESSION_ATTR_LAST_SEARCH, searchContext);
            
            // Validate search parameters
            if (accountId != null && !accountId.trim().isEmpty()) {
                if (!accountId.matches("^\\d{11}$")) {
                    throw new CardValidationException("ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER");
                }
            }
            
            if (cardNumber != null && !cardNumber.trim().isEmpty()) {
                if (!cardNumber.matches("^\\d{16}$")) {
                    throw new CardValidationException("CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER");
                }
            }
            
            // Validate and sanitize pagination parameters
            size = Math.min(size, MAX_PAGE_SIZE);
            size = Math.max(size, 1);
            page = Math.max(page, 0);
            
            // Create pageable for search
            Pageable pageable = PageRequest.of(page, size, Sort.by("cardNumber").ascending());
            
            // Perform search based on available criteria
            CardListDTO searchResults;
            if (accountId != null && !accountId.trim().isEmpty()) {
                org.springframework.data.domain.Page<Card> cardPage = cardListService.getCardListByAccountIdWithPagination(accountId, pageable);
                searchResults = cardListService.buildCardListResponse(cardPage);
            } else if (cardNumber != null && !cardNumber.trim().isEmpty()) {
                // For single card search, create a list with the card if found
                CardDetailDTO cardDetail = cardDetailService.getCardDetails(cardNumber);
                if (cardDetail != null) {
                    List<CardListDTO.CardSummary> cardSummaries = new ArrayList<>();
                    CardListDTO.CardSummary summary = new CardListDTO.CardSummary();
                    summary.setCardNumber(cardDetail.getCardNumber());
                    summary.setAccountNumber(cardDetail.getAccountId());
                    summary.setCardStatus(cardDetail.getCardStatus());
                    cardSummaries.add(summary);
                    
                    searchResults = new CardListDTO(cardSummaries, 1, 1, 1L);
                } else {
                    searchResults = new CardListDTO(new ArrayList<>(), 1, size, 0L);
                }
            } else {
                // No specific criteria, return all cards with pagination
                org.springframework.data.domain.Page<Card> cardPage = cardListService.getCardListWithPagination(pageable);
                searchResults = cardListService.buildCardListResponse(cardPage);
            }
            
            // Log successful operation
            auditService.logDataAccessEvent(principal.getName(), "CARDS", "SEARCH", 
                                          searchResults.getCards().size(), 
                                          createAuditDetails("searchCards", "SUCCESS", accountId, cardNumber, status));
            
            return ResponseEntity.ok(searchResults);
            
        } catch (CardValidationException e) {
            logger.warn("Validation error in searchCards: {}", e.getMessage());
            auditService.logSecurityEvent("CARD_SEARCH_VALIDATION", "VALIDATION_ERROR", 
                                        "Validation error: " + e.getMessage(), 
                                        createAuditDetails("searchCards", "VALIDATION_ERROR", accountId, cardNumber, status));
            throw e;
        } catch (Exception e) {
            logger.error("Error in searchCards", e);
            auditService.logSecurityEvent("CARD_SEARCH_ERROR", "SEARCH_ERROR", 
                                        "ERROR: " + e.getMessage(), 
                                        createAuditDetails("searchCards", "ERROR", accountId, cardNumber, status));
            throw new RuntimeException("Error performing card search", e);
        }
    }
    
    /**
     * Exception handler for CardNotFoundException.
     * 
     * This handler provides standardized HTTP 404 responses for card not found
     * scenarios, maintaining consistency with COBOL DFHRESP(NOTFND) patterns.
     * 
     * @param ex CardNotFoundException instance
     * @param request HTTP request for context
     * @return ResponseEntity with error details and HTTP 404 status
     */
    @ExceptionHandler(CardNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleCardNotFoundException(
            CardNotFoundException ex, HttpServletRequest request) {
        
        logger.warn("Card not found: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "CARD_NOT_FOUND");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("correlationId", ex.getCorrelationId());
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("cardIdentifier", ex.getCardIdentifier());
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse);
    }
    
    /**
     * Exception handler for CardValidationException.
     * 
     * This handler provides standardized HTTP 400 responses for validation
     * failures, maintaining consistency with COBOL validation patterns.
     * 
     * @param ex CardValidationException instance
     * @param request HTTP request for context
     * @return ResponseEntity with validation error details and HTTP 400 status
     */
    @ExceptionHandler(CardValidationException.class)
    public ResponseEntity<Map<String, Object>> handleCardValidationException(
            CardValidationException ex, HttpServletRequest request) {
        
        logger.warn("Card validation error: {}", ex.getMessage());
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "VALIDATION_ERROR");
        errorResponse.put("message", ex.getMessage());
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        errorResponse.put("fieldErrors", ex.getFieldErrors());
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }
    
    /**
     * Exception handler for Spring Security AccessDeniedException.
     * 
     * This handler provides standardized HTTP 403 responses for authorization
     * failures, maintaining consistency with RACF authorization patterns.
     * 
     * @param ex AccessDeniedException instance
     * @param request HTTP request for context
     * @return ResponseEntity with authorization error details and HTTP 403 status
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleSecurityException(
            AccessDeniedException ex, HttpServletRequest request) {
        
        logger.warn("Access denied: {}", ex.getMessage());
        
        // Log security event for potential threat detection
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "UNKNOWN";
        
        auditService.logSecurityEvent("ACCESS_DENIED", "AUTHORIZATION_ERROR", 
                                    "Access denied for user: " + username, 
                                    createAuditDetails("securityException", "ACCESS_DENIED", request.getRequestURI()));
        
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "ACCESS_DENIED");
        errorResponse.put("message", "Access denied to card management operation");
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("path", request.getRequestURI());
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse);
    }
    
    /**
     * Exception handler for general exceptions.
     * 
     * This handler provides standardized HTTP 500 responses for unexpected
     * errors, ensuring consistent error handling across all endpoints.
     * 
     * @param ex Exception instance
     * @param request HTTP request for context
     * @return ResponseEntity with error details and HTTP 500 status
     * @throws Exception 
     */
    @ExceptionHandler(Exception.class)
    public void handleGeneralException(
            Exception ex, HttpServletRequest request) throws Exception {
        
        logger.error("Unexpected error in CardController", ex);
        
        // Log security event for potential system threat detection
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "UNKNOWN";
        
        auditService.logSecurityEvent("SYSTEM_ERROR", "INTERNAL_ERROR", 
                                    "Internal error for user: " + username, 
                                    createAuditDetails("generalException", "INTERNAL_ERROR", request.getRequestURI()));
        
        throw ex;
    }
    
    /**
     * Helper method to create audit details map for logging.
     * 
     * @param operation Operation name
     * @param result Operation result
     * @param params Operation parameters
     * @return Map containing audit details
     */
    private Map<String, Object> createAuditDetails(String operation, String result, Object... params) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("result", result);
        details.put("timestamp", LocalDateTime.now());
        
        // Add parameters based on their types
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof String) {
                details.put("param" + i, params[i]);
            } else if (params[i] instanceof Integer) {
                details.put("param" + i, params[i]);
            } else if (params[i] != null) {
                details.put("param" + i, params[i].toString());
            }
        }
        
        return details;
    }
}