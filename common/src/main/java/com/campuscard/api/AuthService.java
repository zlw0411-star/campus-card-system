package com.campuscard.api;
import com.campuscard.dto.*; import com.campuscard.model.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
public interface AuthService { SessionContext login(String username, char[] password); void logout(SessionContext session); User currentUser(SessionContext session); void changePassword(SessionContext session, char[] oldPassword, char[] newPassword); }
