package com.carddemo.entity;

import java.io.Serializable;
import java.util.Objects;

public class CardAccountXrefId implements Serializable {
    
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String cardNumber;
    private String accountId;
    
    // Default constructor
    public CardAccountXrefId() {}
    
    public CardAccountXrefId(String cardNumber, String accountId) {
        this.cardNumber = cardNumber;
        this.accountId = accountId;
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
    
    // equals and hashCode are REQUIRED for composite keys
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardAccountXrefId that = (CardAccountXrefId) o;
        return Objects.equals(cardNumber, that.cardNumber) &&
               Objects.equals(accountId, that.accountId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(cardNumber, accountId);
    }
    
    @Override
    public String toString() {
        return "CardAccountXrefId{" +
                "cardNumber='" + cardNumber + '\'' +
                ", accountId='" + accountId + '\'' +
                '}';
    }
}