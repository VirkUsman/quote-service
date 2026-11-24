package dev.virk.quotes.rates;

public class InvalidRateTableException extends RuntimeException {
    public InvalidRateTableException(String message) {
        super(message);
    }
}
