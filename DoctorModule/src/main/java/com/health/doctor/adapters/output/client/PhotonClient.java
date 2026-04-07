package com.health.doctor.adapters.output.client;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.client.annotation.Client;

@Client("${photon.base-url}")
public interface PhotonClient {
    @Get("/api?q={query}&limit=1")
    PhotonResponse search(String query);
}