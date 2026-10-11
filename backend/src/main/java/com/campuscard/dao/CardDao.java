package com.campuscard.dao;
import java.sql.*; import java.util.*; import java.math.BigDecimal; import java.time.LocalDate; import com.campuscard.model.*; import com.campuscard.dto.*;
public interface CardDao {
Optional<CampusCard> findByUserId(Connection c, long userId, boolean lock) throws SQLException;
CampusCard insert(Connection c, long userId, String cardNo) throws SQLException;
void updateBalance(Connection c, long cardId, BigDecimal balance) throws SQLException;
}
