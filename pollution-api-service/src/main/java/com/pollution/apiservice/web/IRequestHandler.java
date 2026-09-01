package com.pollution.apiservice.web;

/**
 * Answers one kind of request. Handlers speak in {@link Request} and
 * {@link Response} only, so they know nothing of the server that carries
 * them and can be called directly.
 */
public interface IRequestHandler {

    /**
     * @throws IllegalArgumentException if the request asks for something impossible
     *                                  (a bad parameter); the router answers it as a client error
     */
    Response handle(Request request);
}
