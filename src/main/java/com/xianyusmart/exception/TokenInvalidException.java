package com.xianyusmart.exception;

/**
 * WebSocket Token无效异常
 */
public class TokenInvalidException extends RuntimeException {
    
    public TokenInvalidException(String message) {
        super(message);
    }
}
