package com.finledger.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        Account account = accountService.create(request.memberId(), request.accountNumber(), request.initialBalance());
        return AccountResponse.from(account);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(@PathVariable Long accountId) {
        return AccountResponse.from(accountService.get(accountId));
    }

    @PostMapping("/{accountId}/deposits")
    public AccountResponse deposit(@PathVariable Long accountId, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(accountService.deposit(accountId, request.amount()));
    }

    @PostMapping("/{accountId}/withdrawals")
    public AccountResponse withdraw(@PathVariable Long accountId, @Valid @RequestBody AmountRequest request) {
        return AccountResponse.from(accountService.withdraw(accountId, request.amount()));
    }

    public record CreateAccountRequest(
            @NotNull Long memberId,
            @NotBlank String accountNumber,
            @PositiveOrZero long initialBalance
    ) {
    }

    public record AmountRequest(@Positive long amount) {
    }

    public record AccountResponse(
            Long id,
            Long memberId,
            String accountNumber,
            long balance,
            Instant createdAt
    ) {

        static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getMember().getId(),
                    account.getAccountNumber(),
                    account.getBalance(),
                    account.getCreatedAt()
            );
        }
    }
}
