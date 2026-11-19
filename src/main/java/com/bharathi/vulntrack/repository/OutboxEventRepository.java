package com.bharathi.vulntrack.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import com.bharathi.vulntrack.domain.OutboxEvent;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * The oldest unsent messages, locked for this transaction. SKIP LOCKED (timeout -2 on PostgreSQL)
     * means two app instances never take the same row: each takes the rows the other has not locked.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    List<OutboxEvent> findTop100BySentAtIsNullOrderByIdAsc();
}
