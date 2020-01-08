package com.wizzard.dao.impl;

import com.mongodb.client.result.UpdateResult;
import com.wizzard.model.Account;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static org.springframework.data.mongodb.core.query.Criteria.where;
@Service
public class BankMongoDAO {
    @Autowired
    private MongoTemplate mongoTemplate;
    public Optional<Account> updateAccount(String Id, Optional<Account> account) {

        Query query = new Query();
        query.addCriteria(where("_id").is(Id));
        Update updateOp = new Update();
        updateOp.set("balance", account.get().getBalance());
        UpdateResult writeResult = mongoTemplate.updateMulti(query, updateOp, Account.class);
        return account;
    }
}
