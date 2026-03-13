package com.mariabean.reservation.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

        // Common
        INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
        METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method Not Allowed"),
        ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "Entity Not Found"),
        INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "Server Error"),
        INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C005", "Invalid Type Value"),
        TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "C007", "Too Many Requests"),

        // Auth & Security
        UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "Unauthorized Request"),
        FORBIDDEN(HttpStatus.FORBIDDEN, "A002", "Access Denied"),
        TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "A003", "Token is expired"),

        // Facility Domain
        PLACE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "F001", "Place is already registered"),

        // Reservation Domain
        SEAT_ALREADY_BOOKED(HttpStatus.CONFLICT, "R001", "Seat is already booked or locked by another user"),
        RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "R002", "Reservation Details Not Found"),
        RESERVATION_STATUS_INVALID(HttpStatus.BAD_REQUEST, "R003", "Invalid reservation status transition"),
        RESERVATION_TIME_CONFLICT(HttpStatus.CONFLICT, "R004", "Time slot conflicts with an existing reservation"),
        RESERVATION_LOCK_FAILED(HttpStatus.TOO_MANY_REQUESTS, "R005", "Cannot acquire lock, please try again"),
        RESERVATION_CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "R006",
                        "Resource capacity exceeded for the requested time slot"),
        RESERVATION_TIME_MISALIGNED(HttpStatus.BAD_REQUEST, "R007",
                        "Reservation time must be aligned to 30-minute intervals with no seconds"),
        RESERVATION_OWNERSHIP_DENIED(HttpStatus.FORBIDDEN, "R008",
                        "You do not have permission to access this reservation"),

        // Concurrency
        OPTIMISTIC_LOCK_CONFLICT(HttpStatus.CONFLICT, "C006",
                        "The resource was modified by another request. Please retry."),

        // Payment Domain
        PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "P001", "Payment not found"),
        PAYMENT_STATUS_INVALID(HttpStatus.BAD_REQUEST, "P002", "Invalid payment status transition"),
        PAYMENT_CANCEL_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "P003",
                        "Payment cancellation is not allowed in current state"),
        PAYMENT_APPROVAL_FAILED(HttpStatus.BAD_GATEWAY, "P004", "Payment approval failed from PG");

        private final HttpStatus status;
        private final String code;
        private final String message;

}
