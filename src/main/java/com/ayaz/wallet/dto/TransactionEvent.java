package com.ayaz.wallet.dto;

import com.ayaz.wallet.enums.TransactionStatus;
import com.ayaz.wallet.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionEvent {
    private String eventId;
    private String idempotencyKey;
    private String userId;
    private TransactionType type;
    private BigDecimal amount;
    private BigDecimal balanceBefore;
    private BigDecimal balanceAfter;
    private TransactionStatus status;
    private LocalDateTime timestamp;
}