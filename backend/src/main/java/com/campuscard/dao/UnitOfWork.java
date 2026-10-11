package com.campuscard.dao;
import java.sql.*; import java.util.*; import java.math.BigDecimal; import java.time.LocalDate; import com.campuscard.model.*; import com.campuscard.dto.*;
public interface UnitOfWork { <T> T read(SqlWork<T> work); <T> T write(SqlWork<T> work); }
