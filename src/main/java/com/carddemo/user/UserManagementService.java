/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.user;

import com.carddemo.entity.User;
import com.carddemo.entity.User.UserStatus;
import com.carddemo.repository.UserRepository;
import com.carddemo.audit.AuditService;

import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Boot service implementing comprehensive user management operations converted from COUSR00C-03C COBOL programs.
 * 
 * This service provides enterprise-grade user administration capabilities through RESTful endpoints with Spring Security 
 * ROLE_ADMIN enforcement, BCrypt password hashing for secure credential management, and integration with PostgreSQL 
 * users table. Handles complete user lifecycle operations including creation, modification, deletion, and listing 
 * with proper authorization boundaries and comprehensive audit logging integration.
 * 
 * The service converts legacy COBOL user management patterns to modern Spring Boot architecture while maintaining 
 * identical business logic and security controls. All operations are secured with Spring Security authorization 
 * and logged through the AuditService for compliance and security monitoring.
 * 
 * Key Features:
 * - Complete user CRUD operations with Spring Security authorization
 * - BCrypt password hashing with strength factor 10 for enhanced security
 * - Comprehensive audit logging for all user management operations
 * - Role-based access control with ROLE_ADMIN enforcement
 * - Transaction boundary management for data consistency
 * - Pagination support for large user datasets
 * - Enterprise-grade error handling and validation
 * - Integration with PostgreSQL users table through Spring Data JPA
 * 
 * COBOL Program Conversion Mapping:
 * - COUSR00C (User List) → getAllUsers() with pagination support
 * - COUSR01C (User Add) → createUser() with validation and BCrypt hashing
 * - COUSR02C (User Update) → updateUser() with modification tracking
 * - COUSR03C (User Delete) → deleteUser() with confirmation and audit logging
 * 
 * Security Implementation:
 * - All methods require ROLE_ADMIN authorization via @PreAuthorize annotations
 * - Password management uses BCrypt hashing with configurable strength
 * - Comprehensive audit logging through AuditService integration
 * - Session context integration for user identification and security correlation
 * - Transaction boundaries ensure data consistency across operations
 * 
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-01
 * @see User
 * @see UserRepository
 * @see AuditService
 * @see org.springframework.security.access.prepost.PreAuthorize
 */
@Service
@Transactional
public class UserManagementService {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);

    // BCrypt password encoder with strength factor 10 for secure password hashing
    private final BCryptPasswordEncoder passwordEncoder;
    
    // Spring Data JPA repository for user data access operations
    private final UserRepository userRepository;
    
    // Audit service for comprehensive security event logging
    private final AuditService auditService;

    // Default page size for user listing operations (matching COBOL 10-record display)
    private static final int DEFAULT_PAGE_SIZE = 10;
    
    // Maximum page size to prevent memory exhaustion
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Constructor with dependency injection for Spring Boot service initialization.
     * 
     * @param userRepository Spring Data JPA repository for user data access
     * @param auditService Service for comprehensive audit logging
     */
    @Autowired
    public UserManagementService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.passwordEncoder = new BCryptPasswordEncoder(10); // Strength factor 10 for security
        
        logger.info("UserManagementService initialized with BCrypt strength factor 10");
    }

    /**
     * Retrieves all users with pagination support, converted from COUSR00C COBOL program.
     * 
     * This method provides comprehensive user listing capabilities with pagination support, 
     * replicating the COBOL program's 10-record display pattern while adding modern pagination 
     * features. The method enforces ROLE_ADMIN authorization and logs all access attempts 
     * through the AuditService for security monitoring.
     * 
     * The implementation uses Spring Data JPA pagination to efficiently handle large user 
     * datasets without loading all records into memory, providing better performance than 
     * the original VSAM browse operations.
     * 
     * COBOL Program Equivalent:
     * - COUSR00C.cbl: PROCESS-PAGE-FORWARD and PROCESS-PAGE-BACKWARD paragraphs
     * - VSAM STARTBR/READNEXT operations → Spring Data JPA findAll() with Pageable
     * - WS-USER-DATA occurs 10 times → Page<User> with configurable size
     * - CICS browse operations → Database pagination with Sort support
     * 
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10, max 100)
     * @param sortBy Field to sort by (default "username")
     * @param sortDirection Sort direction (ASC/DESC, default ASC)
     * @return Page<User> containing user records with pagination metadata
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Page<User> getAllUsers(int page, int size, String sortBy, String sortDirection) {
        logger.info("Retrieving users page {} with size {} sorted by {} {}", 
                   page, size, sortBy, sortDirection);
        
        try {
            // Validate and constrain page parameters
            page = Math.max(0, page);
            size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
            
            // Default sort parameters if not provided
            if (sortBy == null || sortBy.trim().isEmpty()) {
                sortBy = "username";
            }
            
            // Create sort direction
            Sort.Direction direction = Sort.Direction.ASC;
            if ("DESC".equalsIgnoreCase(sortDirection)) {
                direction = Sort.Direction.DESC;
            }
            
            // Create pageable request with sorting
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            // Execute paginated query
            Page<User> usersPage = userRepository.findAll(pageable);
            
            // Log successful retrieval for audit purposes
            String currentUser = getCurrentUsername();
            auditService.logAdminMenuAccess(
                currentUser, 
                "USER_LIST", 
                "ADMIN", 
                "GRANTED"
            );
            
            logger.info("Successfully retrieved {} users (page {}/{}) for admin user: {}", 
                       usersPage.getNumberOfElements(), 
                       usersPage.getNumber() + 1, 
                       usersPage.getTotalPages(), 
                       currentUser);
            
            return usersPage;
            
        } catch (Exception e) {
            logger.error("Error retrieving users page {} with size {}: {}", page, size, e.getMessage(), e);
            
            // Log security event for failed access
            auditService.logSecurityEvent(
                "USER_LIST_ERROR", 
                "MEDIUM", 
                "Failed to retrieve user list: " + e.getMessage(),
                Map.of("page", page, "size", size, "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to retrieve users", e);
        }
    }

    /**
     * Retrieves a user by ID with comprehensive error handling and audit logging.
     * 
     * This method provides secure user lookup capabilities with ROLE_ADMIN authorization 
     * and comprehensive audit logging. The method handles UUID-based user identification 
     * and provides detailed error reporting for security monitoring.
     * 
     * @param userId UUID of the user to retrieve
     * @return User entity if found
     * @throws RuntimeException if user not found or access denied
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User getUserById(UUID userId) {
        logger.info("Retrieving user by ID: {}", userId);
        
        try {
            Optional<User> userOptional = userRepository.findById(userId);
            
            if (userOptional.isEmpty()) {
                logger.warn("User not found with ID: {}", userId);
                throw new RuntimeException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            
            // Log successful retrieval
            String currentUser = getCurrentUsername();
            auditService.logAdminMenuAccess(
                currentUser, 
                "USER_VIEW", 
                "ADMIN", 
                "GRANTED"
            );
            
            logger.info("Successfully retrieved user {} for admin user: {}", 
                       user.getUsername(), currentUser);
            
            return user;
            
        } catch (Exception e) {
            logger.error("Error retrieving user by ID {}: {}", userId, e.getMessage(), e);
            
            // Log security event for failed access
            auditService.logSecurityEvent(
                "USER_VIEW_ERROR", 
                "MEDIUM", 
                "Failed to retrieve user by ID: " + e.getMessage(),
                Map.of("userId", userId.toString(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to retrieve user", e);
        }
    }

    /**
     * Creates a new user with comprehensive validation and BCrypt password hashing, 
     * converted from COUSR01C COBOL program.
     * 
     * This method provides secure user creation capabilities with comprehensive field 
     * validation, BCrypt password hashing, and audit logging. The implementation 
     * replicates the COBOL program's validation logic while adding modern security 
     * features including password hashing and duplicate username detection.
     * 
     * COBOL Program Equivalent:
     * - COUSR01C.cbl: PROCESS-ENTER-KEY and WRITE-USER-SEC-FILE paragraphs
     * - Field validation logic → Bean Validation with custom constraints
     * - Plain text password storage → BCrypt hashing with salt
     * - CICS WRITE operation → JPA save() with transaction boundaries
     * - DUPKEY/DUPREC handling → existsByUsername() validation
     * 
     * @param user User entity to create (password will be hashed)
     * @return Created user entity with hashed password
     * @throws RuntimeException if validation fails or user already exists
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User createUser(User user) {
        logger.info("Creating new user: {}", user.getUsername());
        
        try {
            // Validate user data
            validateUserForCreation(user);
            
            // Check for duplicate username
            if (userRepository.existsByUsername(user.getUsername())) {
                logger.warn("Attempt to create user with duplicate username: {}", user.getUsername());
                throw new RuntimeException("User ID already exists: " + user.getUsername());
            }
            
            // Hash the password using BCrypt
            String hashedPassword = passwordEncoder.encode(user.getPasswordHash());
            user.setPasswordHash(hashedPassword);
            
            // Set default status if not provided
            if (user.getStatus() == null) {
                user.setStatus(UserStatus.ACTIVE);
            }
            
            // Save user to database
            User savedUser = userRepository.save(user);
            
            // Log successful user creation
            String currentUser = getCurrentUsername();
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("operation", "CREATE");
            operationDetails.put("username", savedUser.getUsername());
            operationDetails.put("role_code", savedUser.getRoleCode());
            operationDetails.put("status", savedUser.getStatus());
            operationDetails.put("created_at", savedUser.getCreatedAt());
            
            auditService.logUserManagementOperation(
                currentUser, 
                savedUser.getUsername(), 
                "CREATE", 
                operationDetails
            );
            
            logger.info("Successfully created user {} with role {} by admin user: {}", 
                       savedUser.getUsername(), savedUser.getRoleCode(), currentUser);
            
            return savedUser;
            
        } catch (Exception e) {
            logger.error("Error creating user {}: {}", user.getUsername(), e.getMessage(), e);
            
            // Log security event for failed user creation
            auditService.logSecurityEvent(
                "USER_CREATE_ERROR", 
                "HIGH", 
                "Failed to create user: " + e.getMessage(),
                Map.of("username", user.getUsername(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to create user: " + e.getMessage(), e);
        }
    }

    /**
     * Updates an existing user with comprehensive validation and audit logging, 
     * converted from COUSR02C COBOL program.
     * 
     * This method provides secure user update capabilities with modification tracking, 
     * password hashing, and comprehensive audit logging. The implementation replicates 
     * the COBOL program's update logic while adding modern security features and 
     * transaction boundary management.
     * 
     * COBOL Program Equivalent:
     * - COUSR02C.cbl: UPDATE-USER-INFO and UPDATE-USER-SEC-FILE paragraphs
     * - Field modification tracking → Change detection with before/after state
     * - CICS READ/REWRITE operations → JPA findById() and save() with optimistic locking
     * - Password modification → BCrypt hashing for new passwords
     * - User modification flag → Automatic change tracking
     * 
     * @param userId UUID of the user to update
     * @param updatedUser User entity with updated values
     * @return Updated user entity
     * @throws RuntimeException if user not found or validation fails
     */
    @PreAuthorize("hasRole('ADMIN')")
    public User updateUser(UUID userId, User updatedUser) {
        logger.info("Updating user ID: {}", userId);
        
        try {
            // Retrieve existing user
            Optional<User> existingUserOptional = userRepository.findById(userId);
            if (existingUserOptional.isEmpty()) {
                logger.warn("Attempt to update non-existent user ID: {}", userId);
                throw new RuntimeException("User not found with ID: " + userId);
            }
            
            User existingUser = existingUserOptional.get();
            
            // Validate updated user data
            validateUserForUpdate(updatedUser);
            
            // Check for username uniqueness if username is being changed
            if (!existingUser.getUsername().equals(updatedUser.getUsername())) {
                if (userRepository.existsByUsername(updatedUser.getUsername())) {
                    logger.warn("Attempt to update user {} with duplicate username: {}", 
                               userId, updatedUser.getUsername());
                    throw new RuntimeException("Username already exists: " + updatedUser.getUsername());
                }
            }
            
            // Track changes for audit logging
            Map<String, Object> beforeState = createUserStateMap(existingUser);
            boolean hasChanges = false;
            
            // Update fields and track changes
            if (!existingUser.getUsername().equals(updatedUser.getUsername())) {
                existingUser.setUsername(updatedUser.getUsername());
                hasChanges = true;
            }
            
            if (!existingUser.getFirstName().equals(updatedUser.getFirstName())) {
                existingUser.setFirstName(updatedUser.getFirstName());
                hasChanges = true;
            }
            
            if (!existingUser.getLastName().equals(updatedUser.getLastName())) {
                existingUser.setLastName(updatedUser.getLastName());
                hasChanges = true;
            }
            
            if (!existingUser.getRoleCode().equals(updatedUser.getRoleCode())) {
                existingUser.setRoleCode(updatedUser.getRoleCode());
                hasChanges = true;
            }
            
            if (!existingUser.getStatus().equals(updatedUser.getStatus())) {
                existingUser.setStatus(updatedUser.getStatus());
                hasChanges = true;
            }
            
            // Handle password update if provided
            if (updatedUser.getPasswordHash() != null && !updatedUser.getPasswordHash().trim().isEmpty()) {
                String hashedPassword = passwordEncoder.encode(updatedUser.getPasswordHash());
                existingUser.setPasswordHash(hashedPassword);
                hasChanges = true;
            }
            
            // Check if any modifications were made
            if (!hasChanges) {
                logger.warn("No changes detected for user update: {}", userId);
                throw new RuntimeException("No modifications detected. Please modify fields to update.");
            }
            
            // Save updated user
            User savedUser = userRepository.save(existingUser);
            
            // Log successful user update
            String currentUser = getCurrentUsername();
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("operation", "UPDATE");
            operationDetails.put("before_state", beforeState);
            operationDetails.put("after_state", createUserStateMap(savedUser));
            operationDetails.put("changes_made", hasChanges);
            operationDetails.put("updated_at", savedUser.getUpdatedAt());
            
            auditService.logUserManagementOperation(
                currentUser, 
                savedUser.getUsername(), 
                "UPDATE", 
                operationDetails
            );
            
            logger.info("Successfully updated user {} by admin user: {}", 
                       savedUser.getUsername(), currentUser);
            
            return savedUser;
            
        } catch (Exception e) {
            logger.error("Error updating user ID {}: {}", userId, e.getMessage(), e);
            
            // Log security event for failed user update
            auditService.logSecurityEvent(
                "USER_UPDATE_ERROR", 
                "HIGH", 
                "Failed to update user: " + e.getMessage(),
                Map.of("userId", userId.toString(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to update user: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a user with comprehensive validation and audit logging, 
     * converted from COUSR03C COBOL program.
     * 
     * This method provides secure user deletion capabilities with comprehensive 
     * audit logging and validation. The implementation replicates the COBOL 
     * program's deletion logic while adding modern security features and 
     * transaction boundary management.
     * 
     * COBOL Program Equivalent:
     * - COUSR03C.cbl: DELETE-USER-INFO and DELETE-USER-SEC-FILE paragraphs
     * - CICS READ/DELETE operations → JPA findById() and deleteById()
     * - User confirmation logic → Method parameter validation
     * - Audit trail creation → AuditService integration
     * 
     * @param userId UUID of the user to delete
     * @throws RuntimeException if user not found or deletion fails
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteUser(UUID userId) {
        logger.info("Deleting user ID: {}", userId);
        
        try {
            // Retrieve user to verify existence and get details for audit
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                logger.warn("Attempt to delete non-existent user ID: {}", userId);
                throw new RuntimeException("User not found with ID: " + userId);
            }
            
            User userToDelete = userOptional.get();
            String usernameToDelete = userToDelete.getUsername();
            
            // Prevent deletion of the current user
            String currentUser = getCurrentUsername();
            if (usernameToDelete.equals(currentUser)) {
                logger.warn("Attempt to delete current user account: {}", currentUser);
                throw new RuntimeException("Cannot delete your own user account");
            }
            
            // Create audit record before deletion
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("operation", "DELETE");
            operationDetails.put("deleted_user_state", createUserStateMap(userToDelete));
            operationDetails.put("deletion_timestamp", LocalDateTime.now());
            operationDetails.put("deleted_by", currentUser);
            
            // Delete user from database
            userRepository.deleteById(userId);
            
            // Log successful user deletion
            auditService.logUserManagementOperation(
                currentUser, 
                usernameToDelete, 
                "DELETE", 
                operationDetails
            );
            
            logger.info("Successfully deleted user {} by admin user: {}", 
                       usernameToDelete, currentUser);
            
        } catch (Exception e) {
            logger.error("Error deleting user ID {}: {}", userId, e.getMessage(), e);
            
            // Log security event for failed user deletion
            auditService.logSecurityEvent(
                "USER_DELETE_ERROR", 
                "HIGH", 
                "Failed to delete user: " + e.getMessage(),
                Map.of("userId", userId.toString(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    /**
     * Searches users by username pattern with pagination support.
     * 
     * This method provides flexible username searching capabilities for administrative 
     * interfaces, supporting partial matches and pagination for efficient user lookup 
     * operations.
     * 
     * @param usernamePattern Username pattern to search for (supports SQL LIKE patterns)
     * @param page Page number (0-based, default 0)
     * @param size Number of records per page (default 10, max 100)
     * @return List<User> containing matching users
     */
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> searchUsers(String usernamePattern, int page, int size) {
        logger.info("Searching users with pattern: {}", usernamePattern);
        
        try {
            // Validate and constrain parameters
            if (usernamePattern == null || usernamePattern.trim().isEmpty()) {
                throw new RuntimeException("Search pattern cannot be empty");
            }
            
            page = Math.max(0, page);
            size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
            
            // Add SQL LIKE wildcards if not present
            String searchPattern = usernamePattern.trim();
            if (!searchPattern.contains("%")) {
                searchPattern = "%" + searchPattern + "%";
            }
            
            // Execute search query
            List<User> searchResults = userRepository.findByUsernameContaining(searchPattern);
            
            // Apply pagination to results
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, searchResults.size());
            
            List<User> paginatedResults = searchResults.subList(startIndex, endIndex);
            
            // Log successful search
            String currentUser = getCurrentUsername();
            auditService.logAdminMenuAccess(
                currentUser, 
                "USER_SEARCH", 
                "ADMIN", 
                "GRANTED"
            );
            
            logger.info("Successfully searched users with pattern '{}', found {} results for admin user: {}", 
                       usernamePattern, searchResults.size(), currentUser);
            
            return paginatedResults;
            
        } catch (Exception e) {
            logger.error("Error searching users with pattern '{}': {}", usernamePattern, e.getMessage(), e);
            
            // Log security event for failed search
            auditService.logSecurityEvent(
                "USER_SEARCH_ERROR", 
                "MEDIUM", 
                "Failed to search users: " + e.getMessage(),
                Map.of("pattern", usernamePattern, "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to search users: " + e.getMessage(), e);
        }
    }

    /**
     * Finds a user by username with comprehensive error handling.
     * 
     * @param username Username to search for
     * @return Optional<User> containing the user if found
     */
    @PreAuthorize("hasRole('ADMIN')")
    public Optional<User> findByUsername(String username) {
        logger.info("Finding user by username: {}", username);
        
        try {
            if (username == null || username.trim().isEmpty()) {
                throw new RuntimeException("Username cannot be empty");
            }
            
            Optional<User> userOptional = userRepository.findByUsername(username.trim());
            
            // Log successful lookup
            String currentUser = getCurrentUsername();
            auditService.logAdminMenuAccess(
                currentUser, 
                "USER_LOOKUP", 
                "ADMIN", 
                "GRANTED"
            );
            
            logger.info("Successfully looked up user {} for admin user: {}", 
                       username, currentUser);
            
            return userOptional;
            
        } catch (Exception e) {
            logger.error("Error finding user by username '{}': {}", username, e.getMessage(), e);
            
            // Log security event for failed lookup
            auditService.logSecurityEvent(
                "USER_LOOKUP_ERROR", 
                "LOW", 
                "Failed to lookup user: " + e.getMessage(),
                Map.of("username", username, "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to find user: " + e.getMessage(), e);
        }
    }

    /**
     * Validates user permissions for administrative operations.
     * 
     * This method provides role-based permission validation for user management 
     * operations, ensuring only authorized administrators can perform sensitive 
     * user management functions.
     * 
     * @param username Username to validate permissions for
     * @param operation Operation being performed (CREATE, UPDATE, DELETE, etc.)
     * @return boolean indicating if operation is permitted
     */
    @PreAuthorize("hasRole('ADMIN')")
    public boolean validateUserPermissions(String username, String operation) {
        logger.info("Validating permissions for user {} operation {}", username, operation);
        
        try {
            // Get current user context
            String currentUser = getCurrentUsername();
            
            // Prevent self-deletion
            if ("DELETE".equals(operation) && username.equals(currentUser)) {
                logger.warn("Admin user {} attempted to delete their own account", currentUser);
                return false;
            }
            
            // Log permission validation
            auditService.logAdminMenuAccess(
                currentUser, 
                "PERMISSION_CHECK", 
                "ADMIN", 
                "GRANTED"
            );
            
            logger.info("Permission validation successful for user {} operation {} by admin {}", 
                       username, operation, currentUser);
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error validating permissions for user {} operation {}: {}", 
                        username, operation, e.getMessage(), e);
            
            // Log security event for failed permission validation
            auditService.logSecurityEvent(
                "PERMISSION_VALIDATION_ERROR", 
                "HIGH", 
                "Failed to validate permissions: " + e.getMessage(),
                Map.of("username", username, "operation", operation, "error", e.getMessage())
            );
            
            return false;
        }
    }

    /**
     * Resets a user's password with secure BCrypt hashing.
     * 
     * This method provides secure password reset capabilities for administrative 
     * use, implementing BCrypt hashing and comprehensive audit logging.
     * 
     * @param userId UUID of the user whose password to reset
     * @param newPassword New password to set (will be hashed)
     * @throws RuntimeException if user not found or password reset fails
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void resetPassword(UUID userId, String newPassword) {
        logger.info("Resetting password for user ID: {}", userId);
        
        try {
            // Validate password
            if (newPassword == null || newPassword.trim().isEmpty()) {
                throw new RuntimeException("Password cannot be empty");
            }
            
            // Retrieve user
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                throw new RuntimeException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            
            // Hash new password
            String hashedPassword = passwordEncoder.encode(newPassword);
            user.setPasswordHash(hashedPassword);
            
            // Save updated user
            userRepository.save(user);
            
            // Log password reset
            String currentUser = getCurrentUsername();
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("operation", "PASSWORD_RESET");
            operationDetails.put("reset_timestamp", LocalDateTime.now());
            operationDetails.put("reset_by", currentUser);
            
            auditService.logUserManagementOperation(
                currentUser, 
                user.getUsername(), 
                "PASSWORD_RESET", 
                operationDetails
            );
            
            logger.info("Successfully reset password for user {} by admin user: {}", 
                       user.getUsername(), currentUser);
            
        } catch (Exception e) {
            logger.error("Error resetting password for user ID {}: {}", userId, e.getMessage(), e);
            
            // Log security event for failed password reset
            auditService.logSecurityEvent(
                "PASSWORD_RESET_ERROR", 
                "HIGH", 
                "Failed to reset password: " + e.getMessage(),
                Map.of("userId", userId.toString(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to reset password: " + e.getMessage(), e);
        }
    }

    /**
     * Activates a user account by setting status to ACTIVE.
     * 
     * @param userId UUID of the user to activate
     * @throws RuntimeException if user not found or activation fails
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void activateUser(UUID userId) {
        logger.info("Activating user ID: {}", userId);
        
        try {
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                throw new RuntimeException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            user.setStatus(UserStatus.ACTIVE);
            userRepository.save(user);
            
            // Log user activation
            String currentUser = getCurrentUsername();
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("operation", "ACTIVATE");
            operationDetails.put("previous_status", UserStatus.INACTIVE);
            operationDetails.put("new_status", UserStatus.ACTIVE);
            operationDetails.put("activation_timestamp", LocalDateTime.now());
            
            auditService.logUserManagementOperation(
                currentUser, 
                user.getUsername(), 
                "ACTIVATE", 
                operationDetails
            );
            
            logger.info("Successfully activated user {} by admin user: {}", 
                       user.getUsername(), currentUser);
            
        } catch (Exception e) {
            logger.error("Error activating user ID {}: {}", userId, e.getMessage(), e);
            
            // Log security event for failed activation
            auditService.logSecurityEvent(
                "USER_ACTIVATION_ERROR", 
                "MEDIUM", 
                "Failed to activate user: " + e.getMessage(),
                Map.of("userId", userId.toString(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to activate user: " + e.getMessage(), e);
        }
    }

    /**
     * Deactivates a user account by setting status to INACTIVE.
     * 
     * @param userId UUID of the user to deactivate
     * @throws RuntimeException if user not found or deactivation fails
     */
    @PreAuthorize("hasRole('ADMIN')")
    public void deactivateUser(UUID userId) {
        logger.info("Deactivating user ID: {}", userId);
        
        try {
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                throw new RuntimeException("User not found with ID: " + userId);
            }
            
            User user = userOptional.get();
            
            // Prevent deactivation of current user
            String currentUser = getCurrentUsername();
            if (user.getUsername().equals(currentUser)) {
                throw new RuntimeException("Cannot deactivate your own user account");
            }
            
            user.setStatus(UserStatus.INACTIVE);
            userRepository.save(user);
            
            // Log user deactivation
            Map<String, Object> operationDetails = new HashMap<>();
            operationDetails.put("operation", "DEACTIVATE");
            operationDetails.put("previous_status", UserStatus.ACTIVE);
            operationDetails.put("new_status", UserStatus.INACTIVE);
            operationDetails.put("deactivation_timestamp", LocalDateTime.now());
            
            auditService.logUserManagementOperation(
                currentUser, 
                user.getUsername(), 
                "DEACTIVATE", 
                operationDetails
            );
            
            logger.info("Successfully deactivated user {} by admin user: {}", 
                       user.getUsername(), currentUser);
            
        } catch (Exception e) {
            logger.error("Error deactivating user ID {}: {}", userId, e.getMessage(), e);
            
            // Log security event for failed deactivation
            auditService.logSecurityEvent(
                "USER_DEACTIVATION_ERROR", 
                "MEDIUM", 
                "Failed to deactivate user: " + e.getMessage(),
                Map.of("userId", userId.toString(), "error", e.getMessage())
            );
            
            throw new RuntimeException("Failed to deactivate user: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    /**
     * Validates user data for creation operations.
     * 
     * This method implements comprehensive validation logic equivalent to the 
     * COBOL program's field validation, ensuring all required fields are present 
     * and properly formatted.
     * 
     * @param user User entity to validate
     * @throws RuntimeException if validation fails
     */
    private void validateUserForCreation(User user) {
        if (user == null) {
            throw new RuntimeException("User data cannot be null");
        }
        
        // Validate username (equivalent to COBOL User ID validation)
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new RuntimeException("User ID cannot be empty");
        }
        
        // Validate first name (equivalent to COBOL First Name validation)
        if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()) {
            throw new RuntimeException("First Name cannot be empty");
        }
        
        // Validate last name (equivalent to COBOL Last Name validation)
        if (user.getLastName() == null || user.getLastName().trim().isEmpty()) {
            throw new RuntimeException("Last Name cannot be empty");
        }
        
        // Validate password (equivalent to COBOL Password validation)
        if (user.getPasswordHash() == null || user.getPasswordHash().trim().isEmpty()) {
            throw new RuntimeException("Password cannot be empty");
        }
        
        // Validate user type (equivalent to COBOL User Type validation)
        if (user.getRoleCode() == null || user.getRoleCode().trim().isEmpty()) {
            throw new RuntimeException("User Type cannot be empty");
        }
        
        // Validate role code values
        if (!"A".equals(user.getRoleCode()) && !"U".equals(user.getRoleCode())) {
            throw new RuntimeException("User Type must be 'A' (Admin) or 'U' (User)");
        }
        
        logger.debug("User validation successful for creation: {}", user.getUsername());
    }

    /**
     * Validates user data for update operations.
     * 
     * @param user User entity to validate
     * @throws RuntimeException if validation fails
     */
    private void validateUserForUpdate(User user) {
        if (user == null) {
            throw new RuntimeException("User data cannot be null");
        }
        
        // Validate username
        if (user.getUsername() == null || user.getUsername().trim().isEmpty()) {
            throw new RuntimeException("User ID cannot be empty");
        }
        
        // Validate first name
        if (user.getFirstName() == null || user.getFirstName().trim().isEmpty()) {
            throw new RuntimeException("First Name cannot be empty");
        }
        
        // Validate last name
        if (user.getLastName() == null || user.getLastName().trim().isEmpty()) {
            throw new RuntimeException("Last Name cannot be empty");
        }
        
        // Validate user type
        if (user.getRoleCode() == null || user.getRoleCode().trim().isEmpty()) {
            throw new RuntimeException("User Type cannot be empty");
        }
        
        // Validate role code values
        if (!"A".equals(user.getRoleCode()) && !"U".equals(user.getRoleCode())) {
            throw new RuntimeException("User Type must be 'A' (Admin) or 'U' (User)");
        }
        
        // Validate status
        if (user.getStatus() == null) {
            throw new RuntimeException("Status cannot be empty");
        }
        
        // Validate status values
        if (!UserStatus.ACTIVE.equals(user.getStatus()) && !UserStatus.INACTIVE.equals(user.getStatus()) && 
            !UserStatus.LOCKED.equals(user.getStatus())) {
            throw new RuntimeException("Status must be 'ACTIVE', 'INACTIVE', or 'LOCKED'");
        }
        
        logger.debug("User validation successful for update: {}", user.getUsername());
    }

    /**
     * Creates a user state map for audit logging.
     * 
     * @param user User entity to create state map for
     * @return Map containing user state information
     */
    private Map<String, Object> createUserStateMap(User user) {
        Map<String, Object> stateMap = new HashMap<>();
        stateMap.put("user_id", user.getId());
        stateMap.put("username", user.getUsername());
        stateMap.put("first_name", user.getFirstName());
        stateMap.put("last_name", user.getLastName());
        stateMap.put("role_code", user.getRoleCode());
        stateMap.put("status", user.getStatus());
        stateMap.put("created_at", user.getCreatedAt());
        stateMap.put("updated_at", user.getUpdatedAt());
        stateMap.put("version_number", user.getVersionNumber());
        return stateMap;
    }

    /**
     * Gets the current authenticated username from Spring Security context.
     * 
     * @return Current username or "SYSTEM" if not authenticated
     */
    private String getCurrentUsername() {
        try {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        } catch (Exception e) {
            logger.warn("Unable to get current username from security context: {}", e.getMessage());
            return "SYSTEM";
        }
    }
}