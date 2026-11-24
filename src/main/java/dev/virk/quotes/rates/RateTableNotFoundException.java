package dev.virk.quotes.rates;

public class RateTableNotFoundException extends RuntimeException {
    public RateTableNotFoundException(String message) {
        super(message);
    }
}
