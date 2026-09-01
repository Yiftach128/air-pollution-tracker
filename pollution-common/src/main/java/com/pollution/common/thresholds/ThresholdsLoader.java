package com.pollution.common.thresholds;

import com.pollution.common.PollutionLogger;
import com.pollution.common.json.JsonException;
import com.pollution.common.json.JsonSupport;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.slf4j.Logger;

/**
 * Reads the {@link Thresholds} from their file. The file's shape is the
 * record's — {@code {"baselines": {"PM2_5": 25, …}, "windowFactors":
 * {"PT24H": 1, …}, "readingFactor": 2}} — and the one to read is the
 * {@code thresholds.json} packaged in this module, the source of truth every
 * service carries, unless a service's {@code Config} hands over the file
 * named by {@code THRESHOLDS_FILE}, for tuning without a rebuild.
 */
public final class ThresholdsLoader {

    private static final Logger logger = PollutionLogger.getLogger(ThresholdsLoader.class);

    /** The packaged thresholds, on the classpath. */
    public static final String RESOURCE = "/thresholds.json";

    private ThresholdsLoader() {
    }

    /**
     * The packaged thresholds.
     *
     * @throws IllegalStateException if the resource is missing or not a valid {@link Thresholds}
     */
    public static Thresholds load() {
        try (InputStream in = ThresholdsLoader.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("the thresholds resource " + RESOURCE + " is missing from the classpath");
            }
            Thresholds thresholds = parse(in.readAllBytes(), RESOURCE);
            logger.info("loaded the packaged thresholds {}", RESOURCE);
            return thresholds;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the thresholds resource " + RESOURCE, e);
        }
    }

    /**
     * The thresholds in the given file.
     *
     * @throws IllegalStateException if the file cannot be read or is not a valid {@link Thresholds}
     */
    public static Thresholds load(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            Thresholds thresholds = parse(Files.readAllBytes(file), file.toString());
            logger.info("loaded thresholds from {}", file);
            return thresholds;
        } catch (IOException e) {
            throw new IllegalStateException("cannot read the thresholds file " + file, e);
        }
    }

    private static Thresholds parse(byte[] json, String origin) {
        try {
            return JsonSupport.fromJson(json, Thresholds.class);
        } catch (JsonException e) {
            throw new IllegalStateException("invalid thresholds in " + origin, e);
        }
    }
}
