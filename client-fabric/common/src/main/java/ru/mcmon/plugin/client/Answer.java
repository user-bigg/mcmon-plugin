package ru.mcmon.plugin.client;

public final class Answer {

    private final byte[] payload;
    private final Refusal refusal;

    private Answer(byte[] payload, Refusal refusal) {
        this.payload = payload;
        this.refusal = refusal;
    }

    static Answer sent(byte[] payload) {
        return new Answer(payload, null);
    }

    static Answer refused(Refusal refusal) {
        return new Answer(null, refusal);
    }

    public boolean isSent() {
        return payload != null;
    }

    public byte[] payload() {
        return payload;
    }

    public Refusal refusal() {
        return refusal;
    }

    @Override
    public String toString() {
        return isSent() ? "Answer[sent]" : "Answer[" + refusal + "]";
    }
}
