package com.campuscard.dto;
import java.math.BigDecimal;
public record Statistics(long users, long enabledUsers, BigDecimal totalBalance, BigDecimal rechargeAmount, BigDecimal consumptionAmount, long transactionCount) {}
