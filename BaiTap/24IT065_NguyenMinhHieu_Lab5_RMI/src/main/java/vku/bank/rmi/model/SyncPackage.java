package vku.bank.rmi.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class SyncPackage implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<Account> accounts = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();

    public SyncPackage() {
    }

    public SyncPackage(List<Account> accounts, List<Transaction> transactions) {
        this.accounts = accounts != null ? accounts : new ArrayList<>();
        this.transactions = transactions != null ? transactions : new ArrayList<>();
    }

    public List<Account> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<Account> accounts) {
        this.accounts = accounts;
    }

    public List<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(List<Transaction> transactions) {
        this.transactions = transactions;
    }
}
