package com.carddemo.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "customer_account_xref", schema = "public")
@IdClass(CustomerAccountXrefId.class)
public class CustomerAccountXref {
    
    @Id
    @Column(name = "customer_id", nullable = false, length = 9)
    private String customerId;
    
    @Id
    @Column(name = "account_id", nullable = false, length = 11)
    private String accountId;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "row_version", nullable = false)
    private Integer rowVersion = 1;
    
    // Foreign key relationships (optional - if you want to navigate to related entities)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", insertable = false, updatable = false)
    private Account account;
    
    // Constructors
    public CustomerAccountXref() {}
    
    public CustomerAccountXref(String customerId, String accountId) {
        this.customerId = customerId;
        this.accountId = accountId;
    }
    
    // Automatically set timestamps
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public String getCustomerId() {
        return customerId;
    }
    
    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }
    
    public String getAccountId() {
        return accountId;
    }
    
    public void setAccountId(String accountId) {
        this.accountId = accountId;
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
    
    public Integer getRowVersion() {
        return rowVersion;
    }
    
    public void setRowVersion(Integer rowVersion) {
        this.rowVersion = rowVersion;
    }
    
    public Customer getCustomer() {
        return customer;
    }
    
    public void setCustomer(Customer customer) {
        this.customer = customer;
    }
    
    public Account getAccount() {
        return account;
    }
    
    public void setAccount(Account account) {
        this.account = account;
    }
    
    // equals and hashCode for composite key
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerAccountXref that = (CustomerAccountXref) o;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(accountId, that.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(customerId, accountId);
    }
    
    @Override
    public String toString() {
        return "CustomerAccountXref{" +
                "customerId='" + customerId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", rowVersion=" + rowVersion +
                '}';
    }
}