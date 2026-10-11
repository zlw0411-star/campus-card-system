package com.campuscard.api;

public record ApplicationServices(AuthService auth, CardService cards, RecordService records, AdminService admin, StatisticsService statistics) {}
