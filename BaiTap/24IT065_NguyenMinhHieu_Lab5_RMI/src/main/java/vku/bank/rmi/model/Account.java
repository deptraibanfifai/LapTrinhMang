package vku.bank.rmi.model;

import java.io.Serializable;
import java.text.NumberFormat;
import java.util.Locale;

public class Account implements Serializable {
    private static final long serialVersionUID = 1L;

    private String accountNumber;
    private String holderName;
    private String pin;
    private double balance;
    private long updatedAt;
    private String updatedBy;
    private boolean deleted;

    public Account() {
    }

    public Account(String accountNumber, String holderName, String pin, double balance,
                   long updatedAt, String updatedBy, boolean deleted) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = balance;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.deleted = deleted;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public String getFormattedBalance() {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        return currencyFormat.format(balance);
    }

    @Override
    public String toString() {
        return accountNumber + " - " + holderName + " (" + getFormattedBalance() + ")";
    }
}
