package com.campuscard.api;
import com.campuscard.dto.*; import com.campuscard.model.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
public interface RecordService { Page<TransactionRecord> query(SessionContext session, TransactionFilter filter); String exportCsv(SessionContext session, TransactionFilter filter); }
