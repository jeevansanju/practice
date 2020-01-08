package com.wizzard.services;

import com.wizzard.dao.AccountDAO;
import com.wizzard.exception.BadRequestException;
import com.wizzard.model.Account;
import com.wizzard.model.AccountType;
import org.apache.commons.collections4.IteratorUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.*;

import java.util.List;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest
public class BankTest {

    @Autowired
    private AccountDAO accountDAO;
    @Autowired
    private Bank bank;

    @Before
    @After
    public void cleanup() {
        accountDAO.deleteAll();
    }

    @Test
    public void testCreateAccount() {
                bank.createAccount("jeevan", 2000.00,AccountType.SAVINGS);
                bank.createAccount("jk", 2000.00, AccountType.CURRENT);
        Iterable<Account> accounts = accountDAO.findAllByAccountType(AccountType.SAVINGS);
        List<Account> list = IteratorUtils.toList(accounts.iterator());
        assertEquals(1,list.size());
    }
    @Test
    public void testDuplicateName() {
        bank.createAccount("jeevan", 8765.00, AccountType.CURRENT);
        try {
            bank.createAccount("jeevan", 10000.00, AccountType.CURRENT);
        } catch (BadRequestException e) {
            assertThat(e.getMessage(), containsString("name already exists"));
        }
    }
    @Test
    public void testInvalidAccount() {
        bank.createAccount("abcd", 8765.00, AccountType.CURRENT);
        try {
            bank.findBalance("567");
        } catch (BadRequestException e) {
            assertThat(e.getMessage(), containsString("Invalid source account"));
        }
    }
    @Test
    public void insufficientBalance(){
        Account fromAccount=bank.createAccount("jeevan",8765.00, AccountType.CURRENT);
        String fromAccountId=fromAccount.getId();
        Account toAccount=bank.createAccount("adsesed",10000.00, AccountType.CURRENT);
        String toAccountId=toAccount.getId();
        try{
        bank.transfer(fromAccountId,toAccountId,200.00);
        }catch (BadRequestException e){
            assertThat(e.getMessage(),containsString("Insufficient balance"));
        }
    }
    @Test
    public void testFindBalance(){
        Account account=bank.createAccount("abcd",8765.00, AccountType.CURRENT);
        Account account1=accountDAO.save(account);
        String id=account1.getId();
        double balance=bank.findBalance(id);
        assertEquals(8765.00,balance,0.00);
    }
    @Test
    public void testDeposit(){
        Account account=bank.createAccount("abcd",8765.00,AccountType.SAVINGS);
        String id=account.getId();
        bank.deposit(id,2000.000);
        double balance=bank.findBalance(id);
        assertEquals(10765,balance,0.00);
    }
    @Test(expected = BadRequestException.class)
    public void nonNegativeAmountDeposit(){
        Account account=bank.createAccount("abcd",8765.00,AccountType.CURRENT);
        Account account1=accountDAO.save(account);
        String id=account1.getId();
        bank.deposit(id,-2000.000);
        double balance=bank.findBalance(id);
        assertEquals(10765,balance,0.00);
    }
    @Test
    public void testTransfer(){
        Account fromAccount=bank.createAccount("abcd",8765.00,AccountType.SAVINGS);
        Account account1=accountDAO.save(fromAccount);
        String fromAccountId=account1.getId();
        Account toAccount=bank.createAccount("abc",10000.00,AccountType.SAVINGS);
        Account account2=accountDAO.save(toAccount);
        String toAccountId=account2.getId();
        assertTrue(bank.transfer(fromAccountId,toAccountId,2000.00));
        double fromAccountBalance=bank.findBalance(fromAccountId);
        double toAccountBalance=bank.findBalance(toAccountId);
        assertEquals(fromAccountBalance,6765,00.00);
        assertEquals(toAccountBalance,12000,00.00);
    }
    @Test(expected = BadRequestException.class)
    public void negetiveAmountTransfer(){
        Account fromAccount=bank.createAccount("abcd",8765.00,AccountType.SAVINGS);
        String fromAccountId=fromAccount.getId();
        Account toAccount=bank.createAccount("abcd",10000.00,AccountType.CURRENT);
        String toAccountId=toAccount.getId();
        bank.transfer(fromAccountId,toAccountId,-2000.00);
    }
}


