package com.wizzard.services;

import com.wizzard.dao.AccountDAO;
import com.wizzard.dao.impl.BankMongoDAO;
import com.wizzard.exception.BadRequestException;
import com.wizzard.model.Account;
import com.wizzard.model.AccountType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class Bank {
    @Autowired
    private BankMongoDAO bankMongoDAO;
    @Autowired
    private AccountDAO  accountDAO;

    public Account createAccount(String name, double balance, AccountType accountType){
        if(accountDAO.existsByName(name)){
            throw new BadRequestException("name already exists");
        }
        Account account1=new Account();
        account1.setName(name);
        account1.setBalance(balance);
        account1.setAccountType(accountType);
        Account createdAccount=accountDAO.save(account1);
        return createdAccount;
    }
    public double findBalance(String accountId) {
        Optional<Account> account = accountDAO.findById(accountId);
        if(!account.isPresent()){
            throw new BadRequestException("Invalid source account");
        }
        double balance =account.get().getBalance();
        return balance;
    }
     public Double deposit(String accountId, double amount){
        Optional<Account> account = accountDAO.findById(accountId);
         if(!account.isPresent()){
             throw new BadRequestException("Invalid source account");
         }
        double intialBalance =account.get().getBalance();
         if(amount<0){
             throw new BadRequestException("Enter valid amount");
         }
        double balance=intialBalance+amount;
        account.get().setBalance(balance);
        bankMongoDAO.updateAccount(accountId,account);
        return balance;
    }
    public boolean transfer(String fromAccountId,String toAccountId,double amount){
    Optional<Account> account = accountDAO.findById(fromAccountId);
    Optional<Account> account2 = accountDAO.findById(toAccountId);
    if(!account.isPresent()&&!account2.isPresent()){
        throw new BadRequestException("Invalid  account ID's");
    }
    if(amount<0){
            throw new BadRequestException("Enter valid amount");
    }
    if(account.get().getBalance() < amount) {
        throw new BadRequestException("Insufficient balance");
    }
    double balance=account.get().getBalance()-amount;
    account.get().setBalance(balance);
    bankMongoDAO.updateAccount(fromAccountId,account);
        Optional<Account> toAccount = accountDAO.findById(toAccountId);
    if(!toAccount.isPresent()){
        throw new BadRequestException("Invalid source account");
    }
        double balance2=account2.get().getBalance()+amount;
    account2.get().setBalance(balance2);
    bankMongoDAO.updateAccount(toAccountId,account2);
    return true;
    }
}