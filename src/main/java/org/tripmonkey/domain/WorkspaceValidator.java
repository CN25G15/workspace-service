package org.tripmonkey.domain;

import io.quarkus.grpc.GrpcClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.tripmonkey.database.service.PersistUserData;
import org.tripmonkey.database.service.UserPlace;
import org.tripmonkey.domain.data.Location;
import org.tripmonkey.domain.patch.PatchVisitor;
import org.tripmonkey.google.places.PlacesClientInterface;
import org.tripmonkey.google.places.data.APIResponse;
import org.tripmonkey.proto.map.ProtoMapper;
import org.tripmonkey.proto.google.places.data.Place;
import org.tripmonkey.rest.domain.WorkspacePatch;
import org.tripmonkey.rest.domain.value.LocationPatch;
import org.tripmonkey.workspace.service.User;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class WorkspaceValidator {

    @Inject
    Logger log;

    @ConfigProperty(name = "api.key.google")
    Optional<String> key;

    @Inject
    @RestClient
    PlacesClientInterface placesClient;

    @GrpcClient("psud")
    PersistUserData psud;

    private void commitToDb(String user, APIResponse r) {
        psud.store(UserPlace.newBuilder()
                .setPlace(Place.newBuilder().setPlaceId(r.getResult().getPlace_id())
                        .setRating(r.getResult().getRating())
                        .addAllType(r.getResult().getTypes())
                        .setGeometry(ProtoMapper.geometryMapper.serialize(r.getResult().getGeometry()))
                        .build())
                .setUser(User.newBuilder().setUserId(user).build())
                .build()).map(status -> {
            if (status.getStatus() != 200) {
                log.errorf("Couldn't store location with user in database.");
            }
            return status;
        });
    }

    private Optional<APIResponse> validate(String user, String place_id) {
        Optional<APIResponse> o = Optional.empty();
        APIResponse r = placesClient.getLocationDetails(key.get(), List.of("place_id", "name", "type", "geometry", "rating"), place_id);
        switch (r.getStatus()) {
            case REQUEST_DENIED -> {
                log.warnf("API key provided for google endpoints is invalid.\nAccepting location as valid even though it may not be valid.");
            }
            case INVALID_REQUEST -> {
            }
            case OK -> {
                o = Optional.of(r);
            }
        }
        return o;
    }

    public boolean validate(PatchVisitor wp) {

        if(key.isEmpty()) {
            log.warnf("No API key for google endpoints.\nAccepting location as valid even though it may not be valid.");
            return true;
        }

        if(wp instanceof LocationPatch lp) {
            return validate(lp.getUser(), lp.getValue().getPlace_id()).map(apiResponse -> {
                commitToDb(lp.getUser(),apiResponse);
                return true;
            }).orElse(false);
        }

        return true;

    }

}
