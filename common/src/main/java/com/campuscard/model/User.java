package com.campuscard.model;
import java.time.LocalDateTime;
public record User(long id, String username, String fullName, String studentNo, Role role, boolean enabled, LocalDateTime createdAt) {}
