package com.campuscard.dao;
import java.sql.*; import java.util.*; import java.math.BigDecimal; import java.time.LocalDate; import com.campuscard.model.*; import com.campuscard.dto.*;
@FunctionalInterface public interface SqlWork<T> { T run(Connection connection) throws SQLException; }
