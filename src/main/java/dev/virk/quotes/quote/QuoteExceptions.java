package dev.virk.quotes.quote;

public final class QuoteExceptions {

    private QuoteExceptions() {}

    public static class QuoteNotFoundException extends RuntimeException {
        public QuoteNotFoundException(long id) {
            super("no quote " + id);
        }
    }

    public static class QuoteExpiredException extends RuntimeException {
        public QuoteExpiredException(long id) {
            super("quote " + id + " has expired");
        }
    }

    public static class QuoteAlreadyAcceptedException extends RuntimeException {
        public QuoteAlreadyAcceptedException(long id) {
            super("quote " + id + " was already accepted");
        }
    }
}
