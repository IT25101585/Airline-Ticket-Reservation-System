package com.skylanka.air.payment.entity;

public enum PaymentMethod {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    BANK_TRANSFER("Bank Transfer");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
