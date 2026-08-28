/**
 * PostgreSQL implementations of the repository abstractions in {@code com.pollution.persistence}.
 * Only the services that read or write the relational store depend on this module; it is the one
 * place in the project that knows the table layout.
 */
package com.pollution.persistence.postgres;
