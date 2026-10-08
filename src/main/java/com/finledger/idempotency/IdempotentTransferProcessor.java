package com.finledger.idempotency;

import com.finledger.transfer.Transfer;
import com.finledger.transfer.TransferService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
public class IdempotentTransferProcessor {
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final TransferService transferService;

    public IdempotentTransferProcessor(IdempotencyRecordRepository idempotencyRecordRepository, TransferService transferService){
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.transferService = transferService;
    }

    @Transactional
    public Transfer process(String key, Long formAccountId, Long toAccountId, long amount){
        IdempotencyRecord record = new IdempotencyRecord(key, formAccountId, toAccountId, amount);

        //DB UNIQUE 제약을 즉시 확인하기 위해 flush
        idempotencyRecordRepository.saveAndFlush(record);

        //기존 TransferService 사용 (Lock Ordering, Ledger, Transaction)
        Transfer transfer = transferService.transfer(formAccountId, toAccountId, amount);

        record.complete(transfer);

        return transfer;
    }
}
