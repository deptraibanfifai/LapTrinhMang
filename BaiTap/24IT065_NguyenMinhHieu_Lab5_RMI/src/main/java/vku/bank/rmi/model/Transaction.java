package vku.bank.rmi.model;

import java.io.Serializable;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Transaction implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String TYPE_DEPOSIT = "DEPOSIT";
    public static final String TYPE_WITHDRAW = "WITHDRAW";
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";

    private String txId;
    private String accountNumber;
    private String txType;
    private double amount;
    private String targetAccount;
    private String description;
    private long createdAt;
    private String createdBy;

    public Transaction() {
    }

    public Transaction(String txId, String accountNumber, String txType, double amount,
                       String targetAccount, String description, long createdAt, String createdBy) {
        this.txId = txId;
        this.accountNumber = accountNumber;
        this.txType = txType;
        this.amount = amount;
        this.targetAccount = targetAccount;
        this.description = description;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public String getTxId() {
        return txId;
    }

    public void setTxId(String txId) {
        this.txId = txId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getTxType() {
        return txType;
    }

    public void setTxType(String txType) {
        this.txType = txType;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getTargetAccount() {
        return targetAccount;
    }

    public void setTargetAccount(String targetAccount) {
        this.targetAccount = targetAccount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getFormattedAmount() {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("vi", "VN"));
        return currencyFormat.format(amount);
    }

    public String getFormattedDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(new Date(createdAt));
    }
}
