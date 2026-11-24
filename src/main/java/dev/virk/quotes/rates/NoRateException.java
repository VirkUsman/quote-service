package dev.virk.quotes.rates;

public class NoRateException extends RuntimeException {
    public NoRateException(String message) {
        super(message);
    }
}
