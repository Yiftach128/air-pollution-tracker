package com.pollution.apiservice.web;

/**
 * The HTTP server that carries requests to an {@link IRequestHandler} and
 * its answers back. Created stopped; {@link #start()} begins listening,
 * {@link #close()} stops it and releases its threads.
 */
public interface IWebServer extends AutoCloseable {

    void start();

    @Override
    void close();
}
