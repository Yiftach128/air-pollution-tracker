package com.pollution.apiservice.testing;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.ILatestReadingStore;
import java.util.List;
import java.util.Optional;

/**
 * An {@link ILatestReadingStore} for the API service's tests: holds the
 * current readings the test gives it and can be told to fail. The API
 * service never writes.
 */
public final class StubLatestReadingStore implements ILatestReadingStore {

    private List<PollutionData> current = List.of();
    private RuntimeException failure;
    private boolean closed;

    public void holds(PollutionData... readings) {
        this.current = List.of(readings);
    }

    /** Makes every lookup throw {@code failure} until told otherwise ({@code null} to answer again). */
    public void failWith(RuntimeException failure) {
        this.failure = failure;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public boolean save(PollutionData reading) {
        throw new UnsupportedOperationException("the API service never writes");
    }

    @Override
    public Optional<PollutionData> find(String source, Pollutant pollutant) {
        failIfToldTo();
        return current.stream()
                .filter(reading -> reading.source().equals(source) && reading.pollutant() == pollutant)
                .findFirst();
    }

    @Override
    public List<PollutionData> findAll() {
        failIfToldTo();
        return current;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void failIfToldTo() {
        if (failure != null) {
            throw failure;
        }
    }
}
