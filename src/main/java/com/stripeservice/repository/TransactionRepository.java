package com.stripeservice.repository;

import com.stripeservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findBySessionId(String sessionId);

    Optional<Transaction> findByPaymentIntentId(String paymentIntentId);
}