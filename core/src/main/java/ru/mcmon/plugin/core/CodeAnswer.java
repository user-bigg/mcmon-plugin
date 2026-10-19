package ru.mcmon.plugin.core;

import java.util.Optional;

public final class CodeAnswer {

    private static final CodeAnswer NOT_ASKED = new CodeAnswer(Optional.empty(), LookupOutcome.CODE_UNAVAILABLE);
    private static final CodeAnswer BEHIND_PROXY = new CodeAnswer(Optional.empty(), LookupOutcome.BEHIND_PROXY);
    private static final CodeAnswer NONE = new CodeAnswer(Optional.empty(), LookupOutcome.NO_CODE);

    private final Optional<LaunchCode> code;
    private final LookupOutcome withoutCode;

    private CodeAnswer(Optional<LaunchCode> code, LookupOutcome withoutCode) {
        this.code = code;
        this.withoutCode = withoutCode;
    }

    public static CodeAnswer answered(byte[] answer) {
        Optional<LaunchCode> code = LaunchCode.fromAnswer(answer);
        return code.isPresent() ? new CodeAnswer(code, LookupOutcome.NO_CODE) : NONE;
    }

    public static CodeAnswer none() {
        return NONE;
    }

    public static CodeAnswer notAsked() {
        return NOT_ASKED;
    }

    public static CodeAnswer behindProxy() {
        return BEHIND_PROXY;
    }

    Optional<LaunchCode> code() {
        return code;
    }

    LookupOutcome withoutCode() {
        return withoutCode;
    }

    @Override
    public String toString() {
        return code.isPresent() ? "CodeAnswer[code]" : "CodeAnswer[" + withoutCode + "]";
    }
}
