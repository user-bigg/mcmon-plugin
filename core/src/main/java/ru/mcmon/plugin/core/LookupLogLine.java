package ru.mcmon.plugin.core;

public final class LookupLogLine {

    private static final int MAX_NICK_LENGTH = 32;

    private LookupLogLine() {}

    public static String format(String nick, Lookup lookup) {
        return PlainText.sanitize(nick, MAX_NICK_LENGTH) + ": " + describe(lookup);
    }

    private static String describe(Lookup lookup) {
        if (!lookup.confirmed()) {
            LookupOutcome outcome = lookup.outcome();
            return outcome == LookupOutcome.NOT_CONFIRMED
                    ? outcome.description()
                    : LookupOutcome.NOT_CONFIRMED.description() + " (" + outcome.description() + ")";
        }
        ConfirmedPlayer player = lookup.player().get();
        String line = "confirmed, player number " + player.id();
        return player.ban().map(ban -> line + ", " + describe(ban)).orElse(line);
    }

    private static String describe(Ban ban) {
        String when = ban.until().map(until -> "banned until " + until).orElse("banned permanently");
        return ban.reason().isEmpty() ? when : when + ": " + ban.reason();
    }
}
