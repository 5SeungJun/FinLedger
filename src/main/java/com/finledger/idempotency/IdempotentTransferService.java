package com.finledger.idempotency;

import com.finledger.transfer.Transfer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class IdempotentTransferService {
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final IdempotentTransferProcessor processor;

    public IdempotentTransferService(IdempotencyRecordRepository idempotencyRecordRepository, IdempotentTransferProcessor processor){
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.processor = processor;
    }

    public Transfer transfer(String key, Long fromAccountId, Long toAccountId, long amount){
        //이미 완료된 요청인지 확인
        Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByKey(key);

        if(existing.isPresent()){
            return resolveExisting(existing.get(), fromAccountId, toAccountId, amount);
        }
        try{
            //신규 송금 처리
            return processor.process(key, fromAccountId, toAccountId, amount);

        } catch (DataIntegrityViolationException e){

            //동시 요청으로 UNIQUE 제약이 충돌한 경우
            //실패한 트랜잭션 밖에서 기록을 다시 조회
            Optional<IdempotencyRecord> record = idempotencyRecordRepository.findByKey(key);

            if(record.isEmpty()){
                throw e;
            }
            return resolveExisting(record.get(), fromAccountId, toAccountId, amount);
        }
    }

    //기존 결과 반환
    private Transfer resolveExisting(IdempotencyRecord record, Long fromAccountId, Long toAccountId, long amount){
        if(!record.matches(fromAccountId, toAccountId, amount)){
            throw new IdempotencyKeyConflictException();
        }

        return record.getTransfer();
    }

}
