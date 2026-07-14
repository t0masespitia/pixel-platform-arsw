package edu.eci.arsw.pixelplatform.loadtest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;

public class Metrics {

    private final LongAdder sent = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final ConcurrentLinkedQueue<Long> latenciesMs = new ConcurrentLinkedQueue<>();

    public void recordSent() {
        sent.increment();
    }

    public void recordError() {
        errors.increment();
    }

    public void recordLatency(long ms) {
        latenciesMs.add(ms);
    }

    public long getSent() {
        return sent.sum();
    }

    public long getErrors() {
        return errors.sum();
    }

    public List<Long> getLatencies() {
        return new ArrayList<>(latenciesMs);
    }
}
