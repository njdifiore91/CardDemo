package com.carddemo.entity;

import java.io.Serializable;
import java.util.Objects;

public class CustomerAccountXrefId implements Serializable {
    
    private String customerId;
    private String accountId;
    
    // Default constructor
    public CustomerAccountXrefId() {}
    
    public CustomerAccountXrefId(String customerId, String accountId) {
        this.customerId = customerId;
        this.accountId = accountId;
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
    
    // equals and hashCode are REQUIRED for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomerAccountXrefId that = (CustomerAccountXrefId) o;
        return Objects.equals(customerId, that.customerId) &&
               Objects.equals(accountId, that.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(customerId, accountId);
    }
    
    @Override
    public String toString() {
        return "CustomerAccountXrefId{" +
                "customerId='" + customerId + '\'' +
                ", accountId='" + accountId + '\'' +
                '}';
    }
}