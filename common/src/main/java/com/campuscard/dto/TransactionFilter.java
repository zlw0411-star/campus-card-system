package com.campuscard.dto;
import java.time.LocalDate; import com.campuscard.model.TransactionType;
public record TransactionFilter(Long userId, TransactionType type, LocalDate from, LocalDate to, int page, int pageSize) {}
