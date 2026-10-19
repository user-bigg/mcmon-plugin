package ru.mcmon.plugin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LaunchCodeTest {

    @Test
    void acceptsOnlyVersionOneAndThirtyTwoLettersOrDigits() {
        assertEquals(
                Codes.CODE,
                LaunchCode.fromAnswer(Codes.answer(Codes.CODE)).orElseThrow().value());

        byte[] otherVersion = Codes.answer(Codes.CODE);
        otherVersion[0] = 2;
        assertFalse(LaunchCode.fromAnswer(otherVersion).isPresent());
        assertFalse(LaunchCode.fromAnswer(Codes.answer(Codes.CODE.substring(1))).isPresent());
        assertFalse(LaunchCode.fromAnswer(Codes.answer(Codes.CODE + "a")).isPresent());
        assertFalse(LaunchCode.fromAnswer(Codes.answer(Codes.CODE.substring(1) + "_"))
                .isPresent());
        byte[] nonAscii = Codes.answer(Codes.CODE);
        nonAscii[5] = (byte) 0xD0;
        assertFalse(LaunchCode.fromAnswer(nonAscii).isPresent());
        assertFalse(LaunchCode.fromAnswer(null).isPresent());
    }

    @Test
    void textFormsNeverShowTheCode() {
        assertFalse(Codes.code().toString().contains(Codes.CODE));
        assertFalse(CodeAnswer.answered(Codes.answer(Codes.CODE)).toString().contains(Codes.CODE));
    }
}
