package com.example.saga.payment.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/** @param maxAmount charges above this amount are declined by the simulated gateway */
@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(@DefaultValue("1000") BigDecimal maxAmount) {
}
