package com.campuscard.dto;

public record CreateUserRequest(String username, String password, String fullName, String studentNo) {}
