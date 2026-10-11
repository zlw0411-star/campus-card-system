package com.campuscard.dto;
import com.campuscard.model.Role;
public record SessionContext(String token, long userId, Role role, String displayName) {}
