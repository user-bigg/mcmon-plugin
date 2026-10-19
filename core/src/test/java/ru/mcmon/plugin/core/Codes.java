package ru.mcmon.plugin.core;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class Codes {

    static final String CODE = "Ab3dEf6hIj9kLm2nOp5qRs8tUv1wXy4z";

    private Codes() {}

    static byte[] answer(String code) {
        byte[] ascii = code.getBytes(StandardCharsets.US_ASCII);
        byte[] answer = new byte[ascii.length + 1];
        answer[0] = 1;
        System.arraycopy(ascii, 0, answer, 1, ascii.length);
        return answer;
    }

    static LaunchCode code() {
        return LaunchCode.fromAnswer(answer(CODE)).orElseThrow();
    }

    static Supplier<CompletableFuture<CodeAnswer>> answered() {
        return given(CodeAnswer.answered(answer(CODE)));
    }

    static Supplier<CompletableFuture<CodeAnswer>> none() {
        return given(CodeAnswer.none());
    }

    static Supplier<CompletableFuture<CodeAnswer>> given(CodeAnswer answer) {
        return () -> CompletableFuture.completedFuture(answer);
    }
}
