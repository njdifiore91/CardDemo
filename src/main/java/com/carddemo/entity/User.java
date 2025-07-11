/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.carddemo.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity representing the PostgreSQL users table, migrated from VSAM USRSEC file.
 * 
 * This entity maps the user data structure from the COBOL CSUSR01Y copybook to modern
 * JPA annotations with enterprise-grade security features including BCrypt password
 * hashing, Spring Security integration, and optimistic locking for concurrency control.
 * 
 * Key Features:
 * - UUID primary key for modern identity management
 * - BCrypt password hashing replacing plain text storage
 * - RACF-equivalent role mapping ('A' for Admin, 'U' for User)
 * - Spring Security UserDetails interface integration support
 * - Optimistic locking via version field
 * - Comprehensive audit trail with created_at/updated_at timestamps
 * - Database constraints matching COBOL field validation rules
 *
 * Database Schema Mapping:
 * - user_id: UUID primary key (replaces 8-character SEC-USR-ID)
 * - username: Unique username for authentication (from SEC-USR-ID)
 * - password_hash: BCrypt-hashed password (replaces SEC-USR-PWD)
 * - role_code: User privilege level 'A'=Admin, 'U'=User (from SEC-USR-TYPE)
 * - first_name: User first name (from SEC-USR-FNAME)
 * - last_name: User last name (from SEC-USR-LNAME)
 * - status: Account status (ACTIVE, INACTIVE, LOCKED)
 * - created_at: Account creation timestamp
 * - updated_at: Last modification timestamp
 * - version_number: JPA optimistic locking version
 *
 * @author CardDemo Migration Team
 * @version 1.0
 * @since 2024-01-01
 */
@Entity
@Table(name = "users", 
       uniqueConstraints = {
           @UniqueConstraint(columnNames = "username", name = "uk_users_username")
       },
       indexes = {
           @Index(name = "idx_users_username", columnList = "username"),
           @Index(name = "idx_users_role_code", columnList = "role_code"),
           @Index(name = "idx_users_status", columnList = "status"),
           @Index(name = "idx_users_created_at", columnList = "created_at")
       })
public class User {

    /**
     * Primary key using UUID for modern identity management.
     * Automatically generated on entity creation.
     */
    @Id
    @Column(name = "user_id", columnDefinition = "UUID", updatable = false)
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Unique username for authentication, mapped from original SEC-USR-ID.
     * Must be unique across all users and cannot be null.
     * Maximum length of 50 characters (expanded from original 8-character limit).
     */
    @Column(name = "user_name", length = 50, nullable = false, unique = true)
    @NotBlank(message = "Username is required")
    @Size(min = 1, max = 50, message = "Username must be between 1 and 50 characters")
    private String username;

    /**
     * BCrypt-hashed password replacing plain text SEC-USR-PWD storage.
     * Provides enterprise-grade security with strength factor 10.
     * Maximum length of 255 characters to accommodate BCrypt hash format.
     */
    @Column(name = "user_password", length = 100, nullable = false)
    @NotBlank(message = "Password hash is required")
    @Size(max = 255, message = "Password hash cannot exceed 255 characters")
    private String passwordHash;

    /**
     * Role code mapping 1-to-1 with RACF user types from SEC-USR-TYPE.
     * 'A' = Administrative User (ROLE_ADMIN in Spring Security)
     * 'U' = Regular User (ROLE_USER in Spring Security)
     * Database constraint ensures only valid values are stored.
     */
    @Column(name = "user_type", length = 10, nullable = false)
    @NotNull(message = "Role code is required")
    @Pattern(regexp = "^[AU]$", message = "Role code must be 'A' (Admin) or 'U' (User)")
    private String roleCode;

    /**
     * User account status for lifecycle management.
     * ACTIVE = Account is active and can authenticate
     * INACTIVE = Account is disabled but can be reactivated
     * LOCKED = Account is locked due to security concerns
     */
    @Column(name = "status", nullable = false)
    @NotBlank(message = "Status is required")
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    /**
     * User first name, mapped from SEC-USR-FNAME.
     * Optional field for user profile information.
     * Maximum length of 50 characters (expanded from original 20).
     */
    @Column(name = "first_name", length = 50)
    @Size(max = 50, message = "First name cannot exceed 50 characters")
    private String firstName;

    /**
     * User last name, mapped from SEC-USR-LNAME.
     * Optional field for user profile information.
     * Maximum length of 50 characters (expanded from original 20).
     */
    @Column(name = "last_name", length = 50)
    @Size(max = 50, message = "Last name cannot exceed 50 characters")
    private String lastName;

    /**
     * Account creation timestamp for audit trail.
     * Automatically set when entity is first persisted.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Last modification timestamp for audit trail.
     * Automatically updated when entity is modified.
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Version number for JPA optimistic locking.
     * Prevents concurrent modification conflicts and replaces CICS record locking.
     * Automatically incremented by JPA on each update.
     */
    @Version
    @Column(name = "row_version", nullable = false)
    private Long versionNumber = 0L;

    /**
     * Default constructor for JPA.
     */
    public User() {
        // Default constructor required by JPA
    }

    /**
     * Constructor with essential fields for user creation.
     * 
     * @param username Unique username for authentication
     * @param passwordHash BCrypt-hashed password
     * @param roleCode User role ('A' or 'U')
     */
    public User(String username, String passwordHash, String roleCode) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roleCode = roleCode;
        this.status = UserStatus.ACTIVE;
    }

    /**
     * Full constructor for complete user creation.
     * 
     * @param username Unique username for authentication
     * @param passwordHash BCrypt-hashed password
     * @param roleCode User role ('A' or 'U')
     * @param firstName User first name
     * @param lastName User last name
     * @param status Account status
     */
    public User(String username, String passwordHash, String roleCode, 
                String firstName, String lastName, UserStatus status) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.roleCode = roleCode;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status != null ? status : UserStatus.ACTIVE;
    }

    /**
     * JPA lifecycle callback to set creation timestamp.
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * JPA lifecycle callback to update modification timestamp.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    /**
     * Gets the user ID (UUID primary key).
     * 
     * @return UUID user identifier
     */
    public UUID getId() {
        return id;
    }

    /**
     * Sets the user ID (UUID primary key).
     * 
     * @param id UUID user identifier
     */
    public void setId(UUID id) {
        this.id = id;
    }

    /**
     * Gets the username for authentication.
     * 
     * @return Username string
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the username for authentication.
     * 
     * @param username Username string
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Gets the BCrypt-hashed password.
     * 
     * @return Password hash string
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * Sets the BCrypt-hashed password.
     * 
     * @param passwordHash Password hash string
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Gets the role code ('A' for Admin, 'U' for User).
     * 
     * @return Role code string
     */
    public String getRoleCode() {
        return roleCode;
    }

    /**
     * Sets the role code ('A' for Admin, 'U' for User).
     * 
     * @param roleCode Role code string
     */
    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    /**
     * Gets the account status.
     * 
     * @return Status string (ACTIVE, INACTIVE, LOCKED)
     */
    public UserStatus getStatus() {
        return status;
    }

    /**
     * Sets the account status.
     * 
     * @param status Status string (ACTIVE, INACTIVE, LOCKED)
     */
    public void setStatus(UserStatus status) {
        this.status = status;
    }

    /**
     * Gets the user first name.
     * 
     * @return First name string
     */
    public String getFirstName() {
        return firstName;
    }

    /**
     * Sets the user first name.
     * 
     * @param firstName First name string
     */
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    /**
     * Gets the user last name.
     * 
     * @return Last name string
     */
    public String getLastName() {
        return lastName;
    }

    /**
     * Sets the user last name.
     * 
     * @param lastName Last name string
     */
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    /**
     * Gets the account creation timestamp.
     * 
     * @return Creation timestamp
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * Sets the account creation timestamp.
     * 
     * @param createdAt Creation timestamp
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Gets the last modification timestamp.
     * 
     * @return Update timestamp
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Sets the last modification timestamp.
     * 
     * @param updatedAt Update timestamp
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Gets the version number for optimistic locking.
     * 
     * @return Version number
     */
    public Long getVersionNumber() {
        return versionNumber;
    }

    /**
     * Sets the version number for optimistic locking.
     * 
     * @param versionNumber Version number
     */
    public void setVersionNumber(Long versionNumber) {
        this.versionNumber = versionNumber;
    }

    // Business Logic Methods

    /**
     * Checks if the user has administrative privileges.
     * 
     * @return true if user is an administrator ('A' role)
     */
    public boolean isAdmin() {
        return "A".equals(this.roleCode);
    }

    /**
     * Checks if the user has regular user privileges.
     * 
     * @return true if user is a regular user ('U' role)
     */
    public boolean isUser() {
        return "U".equals(this.roleCode);
    }

    /**
     * Checks if the user account is active.
     * 
     * @return true if account status is ACTIVE
     */
    public boolean isActive() {
        return UserStatus.ACTIVE.equals(this.status);
    }

    /**
     * Checks if the user account is locked.
     * 
     * @return true if account status is LOCKED
     */
    public boolean isLocked() {
        return "LOCKED".equals(this.status);
    }

    /**
     * Gets the user's full name (first name + last name).
     * 
     * @return Full name string or username if names are not set
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return username;
        }
    }

    /**
     * Gets the Spring Security role name for this user.
     * Maps role_code to Spring Security authority names.
     * 
     * @return "ROLE_ADMIN" for 'A' role, "ROLE_USER" for 'U' role
     */
    public String getSpringSecurityRole() {
        if ("A".equals(this.roleCode)) {
            return "ROLE_ADMIN";
        } else if ("U".equals(this.roleCode)) {
            return "ROLE_USER";
        } else {
            return "ROLE_USER"; // Default fallback
        }
    }

    // Object Methods

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        User user = (User) o;
        return id != null ? id.equals(user.id) : user.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", roleCode='" + roleCode + '\'' +
                ", status='" + status + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", versionNumber=" + versionNumber +
                '}';
    }
    
    public enum UserStatus {
        ACTIVE, INACTIVE, LOCKED
    }
    
}