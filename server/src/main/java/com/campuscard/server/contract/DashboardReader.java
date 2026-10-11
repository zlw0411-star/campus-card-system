package com.campuscard.server.contract;
import java.sql.Connection; import java.sql.SQLException; import java.time.LocalDate;
public interface DashboardReader { DashboardOverview summarize(Connection c, Long userId, LocalDate from, LocalDate to) throws SQLException; }
