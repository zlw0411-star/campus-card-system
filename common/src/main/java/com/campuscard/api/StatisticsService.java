package com.campuscard.api;
import com.campuscard.dto.*; import com.campuscard.model.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
public interface StatisticsService { Statistics summarize(SessionContext session, LocalDate from, LocalDate to); }
