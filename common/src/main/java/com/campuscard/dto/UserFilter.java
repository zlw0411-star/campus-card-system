package com.campuscard.dto;

public record UserFilter(String keyword, Boolean enabled, int page, int pageSize) {}
