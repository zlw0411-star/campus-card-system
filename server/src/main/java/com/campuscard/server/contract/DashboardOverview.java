package com.campuscard.server.contract;
import java.util.List;
public record DashboardOverview(String rechargeAmount, String consumptionAmount, long transactionCount, List<TrendPoint> trend) { public DashboardOverview { trend=List.copyOf(trend); } }
