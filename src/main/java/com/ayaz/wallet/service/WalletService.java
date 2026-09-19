package com.ayaz.wallet.service;

import com.ayaz.wallet.dto.WalletRequest;
import com.ayaz.wallet.dto.WalletResponse;
import com.ayaz.wallet.entity.Transaction;
import com.ayaz.wallet.entity.Wallet;
import com.ayaz.wallet.enums.TransactionStatus;
import com.ayaz.wallet.enums.TransactionType;
import com.ayaz.wallet.repository.TransactionRepository;
import com.ayaz.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String, String> redisTemplate;

    private static final String IDEMPOTENCY_PREFIX = "idempotency:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);

    // ─── CREATE WALLET ───────────────────────────────────────────────────────
    public Wallet createWallet(String userId) {
        if (walletRepository.findByUserId(userId).isPresent()) {
            throw new RuntimeException("Wallet already exists for user: " + userId);
        }
        Wallet wallet = new Wallet(userId);
        return walletRepository.save(wallet);
    }

    public Wallet getWallet(String userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Wallet not found for user: " + userId));
    }

    // ─── DEPOSIT ─────────────────────────────────────────────────────────────
    @Transactional
    public WalletResponse deposit(WalletRequest request) {
        // 1. Check idempotency key in Redis first (fast path)
        String redisKey = IDEMPOTENCY_PREFIX + request.idempotencyKey();
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("Duplicate deposit detected via Redis. Key: {}", request.idempotencyKey());
            return buildResponseFromTransaction(
                transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(), "Duplicate request - returning cached result");
        }

        // 2. Check idempotency key in DB (slow path - handles Redis eviction)
        Optional<Transaction> existingTxn = transactionRepository
                .findByIdempotencyKey(request.idempotencyKey());
        if (existingTxn.isPresent()) {
            log.info("Duplicate deposit detected via DB. Key: {}", request.idempotencyKey());
            redisTemplate.opsForValue().set(redisKey, "exists", IDEMPOTENCY_TTL);
            return buildResponseFromTransaction(existingTxn.get(), "Duplicate request - returning cached result");
        }

        // 3. Process deposit with optimistic locking retry
        return processWithRetry(request, TransactionType.DEPOSIT);
    }

    // ─── WITHDRAW ────────────────────────────────────────────────────────────
    @Transactional
    public WalletResponse withdraw(WalletRequest request) {
        // Same idempotency check
        String redisKey = IDEMPOTENCY_PREFIX + request.idempotencyKey();
        String cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            log.info("Duplicate withdrawal detected via Redis. Key: {}", request.idempotencyKey());
            return buildResponseFromTransaction(
                transactionRepository.findByIdempotencyKey(request.idempotencyKey())
                    .orElseThrow(), "Duplicate request - returning cached result");
        }

        Optional<Transaction> existingTxn = transactionRepository
                .findByIdempotencyKey(request.idempotencyKey());
        if (existingTxn.isPresent()) {
            log.info("Duplicate withdrawal detected via DB. Key: {}", request.idempotencyKey());
            redisTemplate.opsForValue().set(redisKey, "exists", IDEMPOTENCY_TTL);
            return buildResponseFromTransaction(existingTxn.get(), "Duplicate request - returning cached result");
        }

        return processWithRetry(request, TransactionType.WITHDRAWAL);
    }

    // ─── ROLLBACK ────────────────────────────────────────────────────────────
    @Transactional
    public WalletResponse rollback(String originalIdempotencyKey, String rollbackKey) {
        Transaction original = transactionRepository
                .findByIdempotencyKey(originalIdempotencyKey)
                .orElseThrow(() -> new RuntimeException("Original transaction not found"));

        if (original.getStatus() == TransactionStatus.ROLLED_BACK) {
            throw new RuntimeException("Transaction already rolled back");
        }

        Wallet wallet = original.getWallet();
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter;

        // Reverse the original transaction
        if (original.getType() == TransactionType.DEPOSIT) {
            balanceAfter = balanceBefore.subtract(original.getAmount());
            if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw new RuntimeException("Insufficient balance for rollback");
            }
        } else {
            balanceAfter = balanceBefore.add(original.getAmount());
        }

        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        original.setStatus(TransactionStatus.ROLLED_BACK);
        transactionRepository.save(original);

        Transaction rollbackTxn = new Transaction();
        rollbackTxn.setIdempotencyKey(rollbackKey);
        rollbackTxn.setWallet(wallet);
        rollbackTxn.setType(TransactionType.ROLLBACK);
        rollbackTxn.setAmount(original.getAmount());
        rollbackTxn.setBalanceBefore(balanceBefore);
        rollbackTxn.setBalanceAfter(balanceAfter);
        rollbackTxn.setStatus(TransactionStatus.SUCCESS);
        rollbackTxn.setReferenceId(originalIdempotencyKey);
        transactionRepository.save(rollbackTxn);

        log.info("Rollback successful for txn: {}", originalIdempotencyKey);
        return buildResponseFromTransaction(rollbackTxn, "Rollback successful");
    }

    // ─── CORE PROCESSING WITH OPTIMISTIC LOCKING RETRY ──────────────────────
    private WalletResponse processWithRetry(WalletRequest request, TransactionType type) {
        int maxRetries = 3;
        int attempt = 0;

        while (attempt < maxRetries) {
            try {
                return doProcess(request, type);
            } catch (ObjectOptimisticLockingFailureException e) {
                attempt++;
                log.warn("Optimistic lock conflict on attempt {}. Retrying...", attempt);
                if (attempt >= maxRetries) {
                    throw new RuntimeException("Transaction failed after " + maxRetries + " retries due to concurrent modification");
                }
            }
        }
        throw new RuntimeException("Unexpected error in processWithRetry");
    }

    @Transactional
    private WalletResponse doProcess(WalletRequest request, TransactionType type) {
        Wallet wallet = walletRepository.findByUserId(request.userId())
                .orElseThrow(() -> new RuntimeException("Wallet not found: " + request.userId()));

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter;

        if (type == TransactionType.DEPOSIT) {
            balanceAfter = balanceBefore.add(request.amount());
        } else {
            // WITHDRAWAL — check sufficient funds
            if (balanceBefore.compareTo(request.amount()) < 0) {
                throw new RuntimeException("Insufficient balance. Available: " + balanceBefore);
            }
            balanceAfter = balanceBefore.subtract(request.amount());
        }

        // Update wallet balance — @Version handles optimistic locking
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);

        // Save transaction record
        Transaction txn = new Transaction();
        txn.setIdempotencyKey(request.idempotencyKey());
        txn.setWallet(wallet);
        txn.setType(type);
        txn.setAmount(request.amount());
        txn.setBalanceBefore(balanceBefore);
        txn.setBalanceAfter(balanceAfter);
        txn.setStatus(TransactionStatus.SUCCESS);
        transactionRepository.save(txn);

        // Cache in Redis so next duplicate is caught instantly
        String redisKey = IDEMPOTENCY_PREFIX + request.idempotencyKey();
        redisTemplate.opsForValue().set(redisKey, "exists", IDEMPOTENCY_TTL);

        log.info("{} of {} for user {} successful. Balance: {} -> {}",
                type, request.amount(), request.userId(), balanceBefore, balanceAfter);

        return buildResponseFromTransaction(txn, type + " successful");
    }

    // ─── HELPER ──────────────────────────────────────────────────────────────
    private WalletResponse buildResponseFromTransaction(Transaction txn, String message) {
        return new WalletResponse(
                txn.getIdempotencyKey(),
                txn.getWallet().getUserId(),
                txn.getType(),
                txn.getAmount(),
                txn.getBalanceBefore(),
                txn.getBalanceAfter(),
                txn.getStatus(),
                txn.getCreatedAt(),
                message
        );
    }
}