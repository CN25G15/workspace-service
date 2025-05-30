package org.tripmonkey.domain;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.TimeoutException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.tripmonkey.database.service.PersistUserData;
import org.tripmonkey.database.service.UserPlace;
import org.tripmonkey.domain.patch.PatchVisitor;
import org.tripmonkey.google.places.PlacesClientInterface;
import org.tripmonkey.google.places.data.APIResponse;
import org.tripmonkey.google.places.data.ResponseStatus;
import org.tripmonkey.patch.data.Status;
import org.tripmonkey.proto.map.ProtoMapper;
import org.tripmonkey.proto.google.places.data.Place;
import org.tripmonkey.rest.domain.value.LocationPatch;
import org.tripmonkey.workspace.service.User;

import java.time.Duration;
import java.util.List;
import java.util.Optional;


@ApplicationScoped
// @RunOnVirtualThread // RestClient blocking
public class LocationValidator {

    @Inject
    Logger log;

    @ConfigProperty(name = "api.key.google")
    Optional<String> key;

    @Inject
    @RestClient
    PlacesClientInterface placesClient;

/*
    public static class NoKeyProvidedException extends RuntimeException {
        @Override
        public String getMessage() {
            return "No API key for google endpoints.\nAccepting location as valid even though it may not be valid.";
        }
    }

    public static class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message){ super(message); }
    }

    public static class InvalidKeyException extends RuntimeException {
        public InvalidKeyException(String message){ super(message); }
    }


    private Uni<APIResponse> googlePlacesCall(String key, String place_id) {
        return placesClient.getLocationDetails(key ,
                String.join(",",List.of("place_id", "name", "type", "geometry", "rating")),
                place_id).runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onFailure()
                .invoke(throwable -> log.error(throwable.getLocalizedMessage()));
    }


    // refactored because can't mix reactive pipeline with blocking function - refactored from boolean to Uni<Boolean>
    public Uni<APIResponse> validate(String place_id) {
        return Uni.createFrom().optional(key).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .ifNoItem().after(Duration.ofMillis(100)).failWith(NoKeyProvidedException::new)
                .onItem().transformToUni(key -> placesClient.getLocationDetails(
                        key, String.join(",", List.of("place_id", "name", "type", "geometry", "rating")),
                        place_id)
                ).onItem().transform(apiResponse -> switch (apiResponse.getStatus()) {
                    case OK -> apiResponse;
                    case REQUEST_DENIED -> throw new InvalidKeyException(apiResponse.getErrorMessage());
                    case INVALID_REQUEST -> throw new InvalidRequestException(apiResponse.getErrorMessage());
                    default -> throw new IllegalStateException("Unexpected value: " + apiResponse.getStatus());
                })
                // If invalid key, just accept it but log that it might not be valid
                .onFailure(throwable -> throwable instanceof NoKeyProvidedException || throwable instanceof InvalidKeyException)
                .invoke(throwable -> log.warn(throwable.getMessage()))
                .onFailure(throwable -> throwable instanceof NoKeyProvidedException || throwable instanceof InvalidKeyException)
                .recoverWithItem(APIResponse.newUnkown(place_id));
    }
        /*
        return Uni.createFrom().optional(key).ifNoItem().after(Duration.ofMillis(200)).failWith(NoKeyProvidedException::new)
                .onItem().transformToUni(key -> googlePlacesCall(key,place_id))
                .onItem().invoke(apiResponse -> log.infof("Received API Response for %s", apiResponse.getResult().getName()))
                .onItem()
                .transformToUni(apiResponse ->  switch (apiResponse.getStatus()) {
                        case OK -> apiResponse;
                        case REQUEST_DENIED -> throw new NoKeyProvidedException();
                        case INVALID_REQUEST -> throw new InvalidRequestException(apiResponse.getErrorMessage());
                        default -> throw new IllegalStateException("Unexpected value: " + apiResponse.getStatus());
                })
                .onItem().transform(status -> true)
                .onFailure(throwable -> throwable instanceof TimeoutException)
                .invoke(throwable -> log.info("Google API did not respond within the timeout timeframe."))
                .onFailure(throwable -> throwable instanceof InvalidRequestException)
                .invoke(throwable -> log.info("Submitted location isn't valid"))
                .onFailure(throwable -> throwable instanceof  NoKeyProvidedException)
                .invoke(() -> log.info("No API key for google endpoints.\nAccepting location as valid even though it may not be valid."))
                .onFailure(throwable -> throwable instanceof InvalidRequestException).recoverWithItem(false)
                .onFailure(throwable -> throwable instanceof  NoKeyProvidedException).recoverWithItem(true)
                .onFailure(throwable -> throwable instanceof  APICallException).invoke(throwable -> log.error(throwable))
                .onFailure().recoverWithItem(false);/
    }*/

}
// TODO REFACTOR AND RE THINK THIS PIPELINE AT LEAST