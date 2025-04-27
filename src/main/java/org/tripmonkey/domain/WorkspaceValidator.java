package org.tripmonkey.domain;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.tripmonkey.google.places.PlacesClientInterface;

import java.util.Optional;

@ApplicationScoped
public class WorkspaceValidator {

    @ConfigProperty(name = "api.key.google")
    Optional<String> key;

    @Inject
    @RestClient
    PlacesClientInterface placesClient;

}
