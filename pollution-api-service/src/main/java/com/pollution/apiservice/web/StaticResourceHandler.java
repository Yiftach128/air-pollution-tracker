package com.pollution.apiservice.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Serves the files of the dashboard from a directory on the classpath: the
 * pages are opened at their own paths ({@code /} is the overview,
 * {@code /history} the history of one source), any other path names a file
 * under that directory. Only plain file names of known types are looked
 * up, so a request cannot reach outside the directory or list it.
 */
public final class StaticResourceHandler implements IRequestHandler {

    /** The pages, by the path they are opened at. */
    private static final Map<String, String> PAGES_BY_PATH = Map.of(
            "/", "index.html",
            "/history", "history.html");

    /**
     * Files under here are pinned third-party libraries, served cacheable
     * for good ({@link Response#longLived}); upgrading one means giving the
     * new version a new file name, or browsers keep the old one for a week.
     */
    private static final String LONG_LIVED_DIRECTORY = "vendor/";

    /** Path segments of word characters, dots and dashes, separated by single slashes. */
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*(/[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*)*");

    private static final Map<String, String> CONTENT_TYPES_BY_EXTENSION = Map.of(
            "html", "text/html; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "json", "application/json; charset=utf-8",
            "svg", "image/svg+xml",
            "png", "image/png",
            "ico", "image/x-icon");

    private final String resourceRoot;

    /** @param resourceRoot the classpath directory the files live in, without slashes ({@code static}) */
    public StaticResourceHandler(String resourceRoot) {
        this.resourceRoot = Objects.requireNonNull(resourceRoot, "resourceRoot");
    }

    @Override
    public Response handle(Request request) {
        String path = request.path();
        String file = PAGES_BY_PATH.getOrDefault(path, path.substring(1));
        if (!SAFE_PATH.matcher(file).matches()) {
            return Response.notFound(path);
        }
        String contentType = CONTENT_TYPES_BY_EXTENSION.get(extensionOf(file));
        if (contentType == null) {
            return Response.notFound(path);
        }
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(resourceRoot + "/" + file)) {
            if (in == null) {
                return Response.notFound(path);
            }
            return file.startsWith(LONG_LIVED_DIRECTORY)
                    ? Response.longLived(contentType, in.readAllBytes())
                    : Response.bytes(contentType, in.readAllBytes());
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read static resource " + file, e);
        }
    }

    private static String extensionOf(String file) {
        int dot = file.lastIndexOf('.');
        return dot < 0 ? "" : file.substring(dot + 1).toLowerCase();
    }
}
