package com.carddemo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(JpaSystemException.class)
    public ResponseEntity<Map<String, String>> handleJpaSystemException(JpaSystemException ex) {
        logger.error("Database commit error", ex);
        ex.printStackTrace();
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", "DATABASE_ERROR",
                "message", ex.getMostSpecificCause().getMessage()
            ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        logger.error("Authentication failed", ex);
        ex.printStackTrace();
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(Map.of(
                "error", "UNAUTHORIZED",
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDeniedException(AccessDeniedException ex) {
        logger.error("Access denied", ex);
        ex.printStackTrace();
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(Map.of(
                "error", "ACCESS_DENIED",
                "message", ex.getMessage()
            ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleAllOtherExceptions(Exception ex) {
        logger.error("Unexpected server error", ex);
        ex.printStackTrace();
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of(
                "error", "INTERNAL_SERVER_ERROR",
                "message", "An unexpected error occurred. Please check the logs for details."
            ));
    }

}
