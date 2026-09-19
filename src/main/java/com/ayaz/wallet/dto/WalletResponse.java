package com.ayaz.wallet.dto;

import com.ayaz.wallet.enums.TransactionStatus;
import com.ayaz.wallet.enums.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WalletResponse(
    String idempotencyKey,
    String userId,
    TransactionType type,
    BigDecimal amount,
    BigDecimal balanceBefore,
    BigDecimal balanceAfter,
    TransactionStatus status,
    LocalDateTime createdAt,
    String message
) {}