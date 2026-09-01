package com.pollution.apiservice.config;

import static com.pollution.persistence.config.Config.getLatestReadingTtl;
import static com.pollution.persistence.postgres.config.Config.getPostgresJdbcUrl;
import static com.pollution.persistence.postgres.config.Config.getPostgresPassword;
import static com.pollution.persistence.postgres.config.Config.getPostgresPoolSize;
import static com.pollution.persistence.postgres.config.Config.getPostgresUser;
import static com.pollution.persistence.redis.config.Config.getRedisHost;
import static com.pollution.persistence.redis.config.Config.getRedisPort;

import com.pollution.apiservice.queries.HistoryQuery;
import com.pollution.apiservice.queries.SourceOverviewQuery;
import com.pollution.apiservice.web.HistoryHandler;
import com.pollution.apiservice.web.IRequestHandler;
import com.pollution.apiservice.web.IWebServer;
import com.pollution.apiservice.web.LimitsHandler;
import com.pollution.apiservice.web.PollutantsHandler;
import com.pollution.apiservice.web.Router;
import com.pollution.apiservice.web.SourcesHandler;
import com.pollution.apiservice.web.StaticResourceHandler;
import com.pollution.apiservice.web.jdk.JdkWebServer;
import com.pollution.common.entities.PollutionData;
import com.pollution.persistence.CacheBackedLatestReadingStore;
import com.pollution.persistence.ILatestReadingStore;
import com.pollution.persistence.IPollutionCache;
import com.pollution.persistence.IPollutionRepository;
import com.pollution.persistence.postgres.PostgresPollutionRepository;
import com.pollution.persistence.redis.RedisPollutionCache;

/**
 * Assembles the API service from its {@link Config} values. This is the
 * only place in the service that names concrete implementations — the
 * Postgres repository, the Redis cache, the JDK HTTP server; everything it
 * returns is handed out as an interface.
 */
public final class Wiring {

    private Wiring() {
    }

    /** The history of every source; connects on creation. */
    public static IPollutionRepository createPollutionRepository() {
        return new PostgresPollutionRepository(
                getPostgresJdbcUrl(), getPostgresUser(), getPostgresPassword(), getPostgresPoolSize());
    }

    /** Each series' current reading, as the writer keeps it. */
    public static ILatestReadingStore createLatestReadingStore() {
        IPollutionCache<PollutionData> cache =
                new RedisPollutionCache<>(getRedisHost(), getRedisPort(), PollutionData.class);
        return new CacheBackedLatestReadingStore(cache, getLatestReadingTtl());
    }

    /**
     * The server that answers the dashboard's requests: the JSON API under
     * {@code /api/} and the static files of the page for everything else.
     * Not started yet.
     */
    public static IWebServer createWebServer(IPollutionRepository repository, ILatestReadingStore latestReadings) {
        IRequestHandler router = new Router()
                .route("/api/sources", new SourcesHandler(new SourceOverviewQuery(repository, latestReadings)))
                .route("/api/history", new HistoryHandler(new HistoryQuery(
                        repository, Config.getDefaultHistoryRange(), Config.getMaxHistoryRange())))
                .route("/api/pollutants", new PollutantsHandler(Config.getThresholds()))
                .route("/api/limits", new LimitsHandler(
                        Config.getDefaultHistoryRange(), Config.getMaxHistoryRange()))
                .fallback(new StaticResourceHandler(Config.STATIC_RESOURCE_ROOT));
        return new JdkWebServer(Config.getBindAddress(), Config.getPort(), Config.SERVER_THREADS, router);
    }
}
