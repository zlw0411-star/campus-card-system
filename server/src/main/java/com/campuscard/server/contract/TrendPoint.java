package com.campuscard.server.contract;
import java.time.LocalDate;
public record TrendPoint(LocalDate date, String rechargeAmount, String consumptionAmount) {}
