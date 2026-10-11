package com.campuscard.api;
import com.campuscard.dto.*; import com.campuscard.model.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
public interface CardService { CampusCard getCard(SessionContext session); TransactionRecord recharge(SessionContext session, BigDecimal amount, String description, String operationId); TransactionRecord consume(SessionContext session, BigDecimal amount, String description, String operationId); Optional<TransactionRecord> findOperation(SessionContext session, String operationId); }
