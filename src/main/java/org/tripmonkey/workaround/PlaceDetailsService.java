package org.tripmonkey.workaround;

import io.smallrye.mutiny.Uni;
import io.vertx.core.http.HttpVersion;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.core.Vertx;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.tripmonkey.google.places.data.APIResponse;

import java.util.Optional;


// This class was conjured out of a fit of rage carefully curated by ChatGPT. Rest Client has some serious quirks
//

@Singleton
public class PlaceDetailsService {

    private static final Logger LOG = Logger.getLogger(PlaceDetailsService.class);

    @Inject
    Vertx vertx;// = Vertx.vertx();

    @ConfigProperty(name = "api.key.google")
    Optional<String> key;

    private WebClient client;

    private static final String GOOGLE_API_BASE = "https://maps.googleapis.com";

    @PostConstruct
        //- supposedly makes it eager to create things. leaving tthis here for posterity
    void init() {
        WebClientOptions options = new WebClientOptions()
                /*.setSsl(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setTrustAll(true)
                .setVerifyHost(false)
                .setUseAlpn(true);*/
                .setSsl(true)
                .setProtocolVersion(HttpVersion.HTTP_2)
                .setTrustAll(true)
                .setVerifyHost(false)
                .setUseAlpn(true);

        client = WebClient.create(vertx.getDelegate(), options);
    }

    public Uni<APIResponse> getPlaceDetails(String placeId) {
        if (key.isEmpty() || key.get().isBlank()) {
            LOG.warn("Google API key is not configured — request aborted.");
            return Uni.createFrom().failure(new IllegalStateException("Google API key not configured"));
        }

        return Uni.createFrom().emitter(emitter -> {
            client.getAbs(GOOGLE_API_BASE + "/maps/api/place/details/json")
                    .addQueryParam("place_id", placeId)
                    .addQueryParam("key", key.get())
                    .addQueryParam("fields", "place_id,name,type,geometry,rating")
                    .send()
                    .onSuccess(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                APIResponse details = response.bodyAsJsonObject().mapTo(APIResponse.class);
                                emitter.complete(details);
                            } catch (Exception e) {
                                emitter.fail(new RuntimeException("Failed to parse response: " + e.getMessage(), e));
                            }
                        } else {
                            emitter.fail(new RuntimeException("Non-200 status: " + response.statusCode()));
                        }
                    })
                    .onFailure(emitter::fail);
        });
    }
}
