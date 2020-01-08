package com.wizzard.model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.mapping.Document;
@Slf4j
@Document(collection = "account")
@Getter
@Setter
public class  Account extends AbstractDocument  {

    private String name;
    private Double balance;
    private AccountType accountType;

    public Account(String name,Double balance,AccountType accountType) {
        this.name=name;
        this.balance=balance;
        this.accountType=accountType;
    }
    public Account() {

    }
}
