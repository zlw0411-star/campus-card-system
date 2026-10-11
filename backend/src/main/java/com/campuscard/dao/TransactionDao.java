package com.campuscard.dao;
import java.sql.*; import java.util.*; import java.math.BigDecimal; import java.time.LocalDate; import com.campuscard.model.*; import com.campuscard.dto.*;
public interface TransactionDao {
Optional<TransactionRecord> findByOperationId(Connection c, String operationId) throws SQLException;
TransactionRecord insert(Connection c, long cardId, TransactionType type, BigDecimal amount, BigDecimal before, BigDecimal after, String description, long actorId, String operationId) throws SQLException;
Page<TransactionRecord> query(Connection c, TransactionFilter filter) throws SQLException;
}
