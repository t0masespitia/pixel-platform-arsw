package edu.eci.arsw.pixelplatform.canvas.exception;

public class CooldownActiveException extends RuntimeException {

    private final long remainingMillis;

    public CooldownActiveException(long remainingMillis) {
        super(String.format("Debes esperar %.1f segundos para volver a pintar", remainingMillis / 1000.0));
        this.remainingMillis = remainingMillis;
    }

    public long getRemainingMillis() {
        return remainingMillis;
    }
}
