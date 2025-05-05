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
import org.tripmonkey.google.places.PlacesClientInterface;
import org.tripmonkey.google.places.data.APIResponse;
import org.tripmonkey.proto.domain.ProtoMapper;
import org.tripmonkey.proto.google.places.data.Place;
import org.tripmonkey.rest.domain.WorkspacePatch;
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

    public boolean validate(WorkspacePatch wp) {

        if(wp.getValue().getValue() instanceof Location l) {

            if(key.isPresent()){
                APIResponse r = placesClient.getLocationDetails(key.get(), List.of("place_id","name","type","geometry","rating"), l.getPlaceId());
                switch(r.getStatus()){
                    case REQUEST_DENIED -> {
                        log.warnf("API key provided for google endpoints is invalid.\nAccepting location as valid even though it may not be valid.");
                        return true;
                    }
                    case INVALID_REQUEST -> {
                        return false;
                    }
                    case OK -> {
                        psud.store(UserPlace.newBuilder()
                                .setPlace(Place.newBuilder().setPlaceId(r.getResult().getPlace_id())
                                        .setRating(r.getResult().getRating())
                                        .addAllType(r.getResult().getTypes())
                                        .setGeometry(ProtoMapper.geometryMapper.serialize(r.getResult().getGeometry()))
                                        .build())
                                .setUser(User.newBuilder().setUserId(wp.getUser()).build())
                                .build()).map(status -> {
                                    if(status.getStatus() != 200){
                                        log.errorf("Couldn't store location with user in database.");
                                    }
                                    return status;
                        });
                        return true;
                    }
                }
            } else {
              log.warnf("No API key for google endpoints.\nAccepting location as valid even though it may not be valid.");
            }

        }
        return true;

    }

}
