package com.xianyusmart.service;

/**
 * Result of a delivery request that may have produced an external side effect.
 * Inventory is consumed only after the platform explicitly confirms delivery.
 */
public record DeliveryAttemptResult(Status status, String message) {

    public enum Status {
        CONFIRMED,
        ALREADY_DELIVERED,
        REJECTED,
        UNCERTAIN
    }

    public static DeliveryAttemptResult confirmed(String message) {
        return new DeliveryAttemptResult(Status.CONFIRMED, message);
    }

    public static DeliveryAttemptResult alreadyDelivered(String message) {
        return new DeliveryAttemptResult(Status.ALREADY_DELIVERED, message);
    }

    public static DeliveryAttemptResult rejected(String message) {
        return new DeliveryAttemptResult(Status.REJECTED, message);
    }

    public static DeliveryAttemptResult uncertain(String message) {
        return new DeliveryAttemptResult(Status.UNCERTAIN, message);
    }
}
