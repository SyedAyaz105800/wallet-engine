package com.ayaz.wallet.controller;

import com.ayaz.wallet.dto.WalletRequest;
import com.ayaz.wallet.dto.WalletResponse;
import com.ayaz.wallet.entity.Wallet;
import com.ayaz.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/create/{userId}")
    public ResponseEntity<Wallet> createWallet(@PathVariable String userId) {
        return ResponseEntity.ok(walletService.createWallet(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Wallet> getWallet(@PathVariable String userId) {
        return ResponseEntity.ok(walletService.getWallet(userId));
    }

    @PostMapping("/deposit")
    public ResponseEntity<WalletResponse> deposit(@Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(walletService.deposit(request));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<WalletResponse> withdraw(@Valid @RequestBody WalletRequest request) {
        return ResponseEntity.ok(walletService.withdraw(request));
    }

    @PostMapping("/rollback/{originalKey}/{rollbackKey}")
    public ResponseEntity<WalletResponse> rollback(
            @PathVariable String originalKey,
            @PathVariable String rollbackKey) {
        return ResponseEntity.ok(walletService.rollback(originalKey, rollbackKey));
    }
}