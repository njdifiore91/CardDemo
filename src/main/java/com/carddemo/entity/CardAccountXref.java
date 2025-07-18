package com.carddemo.entity;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "card_account_xref", schema = "public")
@IdClass(CardAccountXrefId.class)
public class CardAccountXref {
    
    @Id
    @Column(name = "card_number", nullable = false, length = 16)
    private String cardNumber;
    
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", referencedColumnName = "account_id", insertable = false, updatable = false)
    private Account account;
    
    // Constructors
    public CardAccountXref() {}
    
    public CardAccountXref(String cardNumber, String accountId) {
        this.cardNumber = cardNumber;
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
        CardAccountXref that = (CardAccountXref) o;
        return Objects.equals(cardNumber, that.cardNumber) &&
               Objects.equals(accountId, that.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber, accountId);
    }
    
    @Override
    public String toString() {
        return "CardAccountXref{" +
                "cardNumber='" + cardNumber + '\'' +
                ", accountId='" + accountId + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", rowVersion=" + rowVersion +
                '}';
    }
}