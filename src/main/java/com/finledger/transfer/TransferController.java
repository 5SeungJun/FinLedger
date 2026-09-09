package com.finledger.transfer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(@Valid @RequestBody TransferRequest request) {
        return TransferResponse.from(
                transferService.transfer(request.fromAccountId(), request.toAccountId(), request.amount())
        );
    }

    @GetMapping("/transfers/{transferId}")
    public TransferResponse get(@PathVariable Long transferId) {
        return TransferResponse.from(transferService.get(transferId));
    }

    @GetMapping("/accounts/{accountId}/transfers")
    public List<TransferResponse> findByAccount(@PathVariable Long accountId) {
        return transferService.findByAccount(accountId).stream()
                .map(TransferResponse::from)
                .toList();
    }

    public record TransferRequest(
            @NotNull Long fromAccountId,
            @NotNull Long toAccountId,
            @Positive long amount
    ) {
    }

    public record TransferResponse(
            Long id,
            Long fromAccountId,
            Long toAccountId,
            long amount,
            TransferStatus status,
            Instant createdAt,
            Instant completedAt
    ) {

        static TransferResponse from(Transfer transfer) {
            return new TransferResponse(
                    transfer.getId(),
                    transfer.getFromAccount().getId(),
                    transfer.getToAccount().getId(),
                    transfer.getAmount(),
                    transfer.getStatus(),
                    transfer.getCreatedAt(),
                    transfer.getCompletedAt()
            );
        }
    }
}
