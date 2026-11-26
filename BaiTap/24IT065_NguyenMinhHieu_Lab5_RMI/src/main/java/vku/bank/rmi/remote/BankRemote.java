package vku.bank.rmi.remote;

import vku.bank.rmi.model.Account;
import vku.bank.rmi.model.SyncPackage;
import vku.bank.rmi.model.Transaction;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

public interface BankRemote extends Remote {
    String SERVICE_NAME = "BankService";

    String ping() throws RemoteException;

    String getServerName() throws RemoteException;

    Account login(String accountNumber, String pin) throws RemoteException;

    Account getAccount(String accountNumber) throws RemoteException;

    List<Account> getAllAccounts() throws RemoteException;

    Account deposit(String accountNumber, double amount, String description) throws RemoteException;

    Account withdraw(String accountNumber, double amount, String description) throws RemoteException;

    Account transfer(String fromAccount, String toAccount, double amount, String description) throws RemoteException;

    List<Transaction> getTransactionHistory(String accountNumber) throws RemoteException;

    List<String> getSyncLogs() throws RemoteException;

    String synchronizeNow() throws RemoteException;

    boolean mergePeerTransaction(Transaction tx, List<Account> updatedAccounts, String sourceServer) throws RemoteException;

    SyncPackage mergeAllFromPeer(SyncPackage incoming, String sourceServer) throws RemoteException;
}
