package com.stockpulse.repository;

import com.stockpulse.entity.Suggestion;
import com.stockpulse.entity.SuggestionStatus;
import com.stockpulse.entity.SuggestionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SuggestionRepository extends JpaRepository<Suggestion, UUID> {
    List<Suggestion> findAllByProductIdOrderByCreatedAtDesc(UUID productId);
    Optional<Suggestion> findByProductIdAndTypeAndTriggerAndStatus(
            UUID productId, SuggestionType type, com.stockpulse.entity.SuggestionTrigger trigger,
            SuggestionStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Suggestion s where s.id = :id")
    Optional<Suggestion> findByIdForUpdate(@Param("id") UUID id);
}
