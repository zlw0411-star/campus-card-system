package com.campuscard.model;
import java.math.BigDecimal; import java.time.LocalDateTime;
public record TransactionRecord(long id, long cardId, long userId, String username, String fullName, TransactionType type, BigDecimal amount, BigDecimal balanceBefore, BigDecimal balanceAfter, String description, long actorId, String operationId, LocalDateTime createdAt) {}
