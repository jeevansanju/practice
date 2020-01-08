package com.wizzard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wizzard.dao.AccountDAO;
import com.wizzard.dao.UserDAO;
import com.wizzard.model.Account;
import com.wizzard.model.AccountType;
import org.apache.commons.collections4.IteratorUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
public class BankControllerTest extends BaseTest {
    @Autowired
    private AccountDAO accountDAO;
    @Autowired
    private UserDAO userDAO;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    @Before
    @After
    public void setup() {
        super.setup(this.userDAO);
        accountDAO.deleteAll();
    }
    @Test
    public void testcreateAccount()throws Exception {
//        Account account = new Account();
//        account.setName("jnf");
//        account.setBalance(700.0);
//        account.setAccountType(AccountType.SAVINGS);
//        final String jsonContent = mapper.writeValueAsString(account);
        String token = "Bearer " + getToken();
        ResultActions resultActions = mvc.perform(MockMvcRequestBuilders.post("/api/v1/bank/addBank?name=evan&balance=5000&accountType=savings")
                .contentType(APPLICATION_JSON_UTF8)
//                .content(jsonContent)
                .header("Authorization", token));
        resultActions.andExpect(status().isOk());
        resultActions.andExpect(jsonPath("$.name").value("evan"));

        resultActions.andExpect(jsonPath("$.balance").value(5000.00));
        resultActions.andExpect(jsonPath("$.accountType").value(AccountType.SAVINGS.toString()));
    }

    @Test
    public void testcreateAccountWithBody()throws Exception {
        Account account =new Account();
        account.setName("jnf");
        account.setBalance(700.0);
        account.setAccountType(AccountType.SAVINGS);
        final String jsonContent = mapper.writeValueAsString(account);
        String token = "Bearer " + getToken();
        ResultActions resultActions = mvc.perform(MockMvcRequestBuilders.post("/api/v1/bank/addBankwithbody/")
                .contentType(APPLICATION_JSON_UTF8).content(jsonContent)
                .header("Authorization", token));
        resultActions.andExpect(status().isOk());
        resultActions.andExpect(jsonPath("$.name").value("jnf"));

        resultActions.andExpect(jsonPath("$.balance").value(700.00));
        resultActions.andExpect(jsonPath("$.accountType").value(AccountType.SAVINGS.toString()));

//        Iterable<Account> accounts = accountDAO.findAllByAccountType(AccountType.SAVINGS.);
//        List<Account> list = IteratorUtils.toList(accounts.iterator());
//        assertEquals(1,list.size());
    }
    @Test
    public void testgetbalance() throws Exception{
        Account account =new Account();
        account.setName("jklm");
        account.setBalance(700.0);
        account.setAccountType(AccountType.SAVINGS);
        accountDAO.save(account);
        final String jsonContent = mapper.writeValueAsString(account);
        String token = "Bearer " + getToken();
        ResultActions resultActions = mvc.perform(MockMvcRequestBuilders.get("/api/v1/bank/getBalance?id="+account.getId())
                .contentType(APPLICATION_JSON_UTF8).content(jsonContent)
                .header("Authorization", token));
        resultActions.andExpect(status().isOk());
        resultActions.andExpect(jsonPath("$").value(700.0));
    }
    @Test
    public void testDeposit()throws Exception{
        Account account =new Account();
        account.setName("jnf");
        account.setBalance(700.0);
        account.setAccountType(AccountType.SAVINGS);
        accountDAO.save(account);
        String jsonContent=mapper.writeValueAsString(account);
        String token = "Bearer " + getToken();
        ResultActions resultActions = mvc.perform(MockMvcRequestBuilders.put("/api/v1/bank/deposit?id="+account.getId()+"&amount="+200.00)
                .contentType(APPLICATION_JSON_UTF8).content(jsonContent)
                .header("Authorization", token));
        resultActions.andExpect(status().isOk());
        resultActions.andExpect(jsonPath("$").value(900.00));

    }
    @Test
    public void testTransfer() throws Exception{
        Account fromAccount=new Account();
        fromAccount.setName("jnf");
        fromAccount.setBalance(700.0);
        fromAccount.setAccountType(AccountType.SAVINGS);
        accountDAO.save(fromAccount);
        Account toAccount =new Account();
        toAccount.setName("fjef");
        toAccount.setBalance(900.0);
        toAccount.setAccountType(AccountType.SAVINGS);
        accountDAO.save(toAccount);
        String jsoncontent =mapper.writeValueAsString(fromAccount);
        String jsonContentSec=mapper.writeValueAsString(toAccount);
        accountDAO.save(toAccount);
        String token = "Bearer " + getToken();
        ResultActions resultActions = mvc.perform(MockMvcRequestBuilders.put("/api/v1/bank/transfer?fromAccountId="+fromAccount.getId()+"&toAccountId="+toAccount.getId()+"&amount="+200.00)
                .contentType(APPLICATION_JSON_UTF8).content(jsoncontent).content(jsonContentSec)
                .header("Authorization", token));
        resultActions.andExpect(status().isOk());
        resultActions.andExpect(jsonPath("$").value(true));

    }
}

