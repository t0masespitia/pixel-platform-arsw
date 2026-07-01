package edu.eci.arsw.pixelplatform.canvas.exception;

public class CooldownActiveException extends RuntimeException {

    private final long remainingSeconds;

    public CooldownActiveException(long remainingSeconds) {
        super("Debes esperar " + remainingSeconds + " segundos para volver a pintar");
        this.remainingSeconds = remainingSeconds;
    }

    public long getRemainingSeconds() {
        return remainingSeconds;
    }
}
