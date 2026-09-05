package com.pollution.alertservice.senders;

import com.pollution.common.entities.PollutionAlert;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An {@link IAlertSender} that delivers nowhere and remembers every alert
 * it was asked to send, so a test can assert what the service raised. Can
 * be told to fail, to test what the service does when its channel is down;
 * a failed send is still remembered as attempted.
 */
public final class RecordingAlertSender implements IAlertSender {

    private final List<PollutionAlert> attempted = new ArrayList<>();
    private final List<PollutionAlert> sent = new ArrayList<>();
    private RuntimeException failure;
    private boolean closed;

    @Override
    public synchronized void send(PollutionAlert alert) {
        Objects.requireNonNull(alert, "alert");
        attempted.add(alert);
        if (failure != null) {
            throw failure;
        }
        sent.add(alert);
    }

    /** Every alert delivered, in order. */
    public synchronized List<PollutionAlert> sent() {
        return List.copyOf(sent);
    }

    /** Every alert the service tried to deliver, delivered or not, in order. */
    public synchronized List<PollutionAlert> attempted() {
        return List.copyOf(attempted);
    }

    /** Makes every send throw {@code failure} until told otherwise ({@code null} to deliver again). */
    public synchronized void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }
}
