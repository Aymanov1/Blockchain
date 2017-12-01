package com.prisch.blocks;

import com.prisch.transactions.Transaction;

import java.util.List;
import java.util.Properties;

public interface Block {

    int version();
    int height();

    List<Transaction> transactions();

    String nonce();

    String previousHash();
    String currentHash();

    Properties properties();
}
