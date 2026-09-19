package com.ayaz.wallet.repository;

import com.ayaz.wallet.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);
    List<Transaction> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}