package com.campuscard.dao;
import java.sql.*; import java.util.*; import java.math.BigDecimal; import java.time.LocalDate; import com.campuscard.model.*; import com.campuscard.dto.*;
public interface UserDao {
Optional<UserAccount> findByUsername(Connection c, String username) throws SQLException;
Optional<UserAccount> findById(Connection c, long id, boolean lock) throws SQLException;
User insert(Connection c, String username, String passwordHash, String fullName, String studentNo) throws SQLException;
User update(Connection c, UpdateUserRequest request) throws SQLException;
void setEnabled(Connection c, long id, boolean enabled) throws SQLException;
void updatePassword(Connection c, long id, String passwordHash) throws SQLException;
Page<User> query(Connection c, UserFilter filter) throws SQLException;
}
