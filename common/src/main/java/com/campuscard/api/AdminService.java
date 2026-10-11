package com.campuscard.api;
import com.campuscard.dto.*; import com.campuscard.model.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.Optional;
public interface AdminService { Page<User> queryUsers(SessionContext session, UserFilter filter); User createUser(SessionContext session, CreateUserRequest request); User updateUser(SessionContext session, UpdateUserRequest request); void setEnabled(SessionContext session, long userId, boolean enabled); }
