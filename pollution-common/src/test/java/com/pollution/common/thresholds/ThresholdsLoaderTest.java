package com.pollution.common.thresholds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pollution.common.entities.Pollutant;
import com.pollution.common.json.JsonSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The packaged {@code thresholds.json} every service carries, and the reading of a replacement file. */
class ThresholdsLoaderTest {

    private static final Duration TEN_MINUTES = Duration.ofMinutes(10);
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofHours(24);

    @Test
    void thePackagedThresholdsCoverEveryPollutantAndEveryWindow() {
        Thresholds thresholds = ThresholdsLoader.load();

        for (Pollutant pollutant : Pollutant.values()) {
            assertTrue(thresholds.baselines().containsKey(pollutant), "baseline for " + pollutant);
        }
        assertTrue(thresholds.windowFactors().keySet().containsAll(List.of(TEN_MINUTES, HOUR, DAY)));
    }

    @Test
    void thePackagedFactorsGrowAsTheMeasurementShortens() {
        Thresholds thresholds = ThresholdsLoader.load();

        double day = thresholds.windowFactors().get(DAY);
        double hour = thresholds.windowFactors().get(HOUR);
        double tenMinutes = thresholds.windowFactors().get(TEN_MINUTES);

        assertEquals(1.0, day, "the 24-hour average is judged by the guideline level itself");
        assertTrue(hour > day);
        assertTrue(tenMinutes > hour);
        assertTrue(thresholds.readingFactor() > tenMinutes);
    }

    @Test
    void readsAFileOfTheSameShape(@TempDir Path dir) throws IOException {
        Map<Pollutant, Double> baselines = new EnumMap<>(Pollutant.class);
        for (Pollutant pollutant : Pollutant.values()) {
            baselines.put(pollutant, 10.0);
        }
        Thresholds expected = new Thresholds(baselines, Map.of(HOUR, 1.5), 3);
        Path file = dir.resolve("thresholds.json");
        Files.writeString(file, JsonSupport.toJson(expected), StandardCharsets.UTF_8);

        assertEquals(expected, ThresholdsLoader.load(file));
    }

    @Test
    void aFileThatIsNotThresholdsIsRejected(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("thresholds.json");
        Files.writeString(file, "{\"baselines\": {\"PM2_5\": 25}}", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class, () -> ThresholdsLoader.load(file));
    }

    @Test
    void aMissingFileIsRejected(@TempDir Path dir) {
        assertThrows(IllegalStateException.class, () -> ThresholdsLoader.load(dir.resolve("missing.json")));
    }
}
