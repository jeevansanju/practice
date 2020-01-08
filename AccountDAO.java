package com.wizzard.dao;

import com.wizzard.model.Account;
import com.wizzard.model.AccountType;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountDAO extends PagingAndSortingRepository<Account,String> {

    Iterable<Account> findAllByAccountType(AccountType accountType);
    boolean existsByName(String str);
}
