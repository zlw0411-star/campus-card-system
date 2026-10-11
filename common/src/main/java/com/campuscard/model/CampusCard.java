package com.campuscard.model;
import java.math.BigDecimal;
public record CampusCard(long id, long userId, String cardNo, BigDecimal balance) {}
