package com.finledger.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long>{
    @Query("select i from IdempotencyRecord i left join fetch i.transfer where i.idempotencyKey = :key")
    Optional<IdempotencyRecord> findByKey(@Param("key") String key);
}
