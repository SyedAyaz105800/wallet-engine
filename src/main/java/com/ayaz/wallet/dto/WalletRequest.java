package com.ayaz.wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WalletRequest(
    @NotBlank String userId,
    @NotBlank String idempotencyKey,
    @NotNull @DecimalMin("0.01") BigDecimal amount
) {}