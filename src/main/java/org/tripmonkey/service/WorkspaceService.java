package org.tripmonkey.service;


import com.google.protobuf.AbstractMessage;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.ItemWithContext;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;
import org.tripmonkey.database.service.FetchWorkspace;
import org.tripmonkey.database.service.PatchPersister;
import org.tripmonkey.database.service.PersistUserData;
import org.tripmonkey.database.service.UserPlace;
import org.tripmonkey.domain.LocationValidator;

import org.tripmonkey.google.places.PlacesClientInterface;
import org.tripmonkey.google.places.data.APIResponse;
import org.tripmonkey.google.places.data.ResponseStatus;
import org.tripmonkey.notification.service.Notification;
import org.tripmonkey.patch.data.Status;
import org.tripmonkey.patch.data.WorkspacePatch;
import org.tripmonkey.proto.google.places.data.Place;
import org.tripmonkey.proto.map.ProtoMapper;
import org.tripmonkey.workaround.PlaceDetailsService;
import org.tripmonkey.workspace.service.PatchApplier;
import org.tripmonkey.workspace.service.User;
import org.tripmonkey.workspace.service.WorkspaceRequest;
import org.tripmonkey.workspace.service.WorkspaceResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@GrpcService
public class WorkspaceService implements PatchApplier {

    private static class FetchWorkspaceException extends RuntimeException {
        public FetchWorkspaceException(Throwable t){
            super(t.getMessage(),t.getCause());
        }
    }

    @Inject
    Logger log;

    @GrpcClient("ppc")
    PatchPersister ppc;

    @GrpcClient("fwc")
    FetchWorkspace fwc;

    @Inject
    KafkaService ks;

    // TODO Implement caching mechanism


    @ConfigProperty(name = "api.key.google")
    Optional<String> key;

    /*
    @Inject
    @RestClient
    PlacesClientInterface placesClient;
    */

    @Inject
    PlaceDetailsService pds;


    @GrpcClient("psud")
    PersistUserData psud;

    private Uni<ItemWithContext<WorkspaceResponse>> fetchWorkspaceFromDatabase(ItemWithContext<WorkspacePatch> iwc){
        return fwc.fetch(WorkspaceRequest.newBuilder().setWid(iwc.get().getWorkspaceId()).build())
                .invoke(workspaceResponse -> log.infof("Found workspace: %s",workspaceResponse.hasWorkspace()))
                .onFailure().transform(FetchWorkspaceException::new)
                .onItem()
                .transform(workspaceResponse -> {
                    ItemWithContext<WorkspaceResponse> ret =
                            new ItemWithContext<WorkspaceResponse>(iwc.context(), workspaceResponse);
                    Notification.Builder nb = Notification.newBuilder();
                    nb.setAction(iwc.get());
                    List<User> l = new ArrayList<User>();
                    if(workspaceResponse.hasWorkspace()){
                        l = workspaceResponse.getWorkspace().getCollaboratorsList();
                    }
                    nb.addAllUsers(l);
                    ret.context().put("notification", nb);
                    ret.context().put("request", iwc.get());
                    return ret;
                })
                .onTermination().invoke((itemWithContext,
                                         throwable,
                                         wasCancelled) -> {
                    if(!wasCancelled && throwable == null)
                        log.infof("Fetched workspace %s from the database successfully.",iwc.get().getWorkspaceId());
                    if(throwable != null)
                        log.errorf("Failed to fetch workspace from the database: %s", throwable);
                    if(wasCancelled)
                        log.infof("Workspace fetch operation was cancelled.");
                });

    }

    /*
    .onFailure().invoke(throwable -> log.errorf(throwable.getMessage(),throwable))
                .onFailure(throwable -> throwable instanceof NoKeyProvidedException).
     */

    // Request validation is just location validation at this moment
    private Uni<ItemWithContext<WorkspaceResponse>> validateRequest(ItemWithContext<WorkspaceResponse> iwc) {
        WorkspacePatch wp = iwc.context().get("request");

        if(!wp.hasLocation())
            return Uni.createFrom().item(iwc);

        return Uni.createFrom().optional(key)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem()
                .transformToUni(key ->
                        pds.getPlaceDetails(wp.getLocation().getPlaceId()))
                .onFailure().invoke(throwable -> log.error(throwable))
                .onItem().transform(apiResponse -> {
                    if(ResponseStatus.OK.equals(apiResponse.getStatus())){
                        iwc.context().put("google_place",apiResponse);
                        return iwc;
                    }
                    throw new RuntimeException(apiResponse.getErrorMessage());
                })
                .onTermination().invoke(() -> log.info("Terminated execution of Response Validation"));

                /*Uni.createFrom().optional(loc_val.validate(wp.getLocation().getPlaceId()).await().asOptional().indefinitely())
                .map(apiResponse -> {
            iwc.context().put("google_place",apiResponse);
            return iwc;
        }).onTermination().invoke((itemWithContext,
                                                  throwable,
                                                  wasCancelled) -> {
            if(!wasCancelled && throwable == null && itemWithContext.context().contains("google_place")){
                APIResponse r = iwc.context().get("google_place");
                log.infof("Fetched Response for place %s from gplaces successfully.", r.getResult().getPlace_id());
            }
            if(!wasCancelled && throwable == null){
                log.infof("Object is not a location.");
            }
            if(throwable != null)
                log.errorf("Failed to fetch place from gplaces:", throwable);
            if(wasCancelled)
                log.infof("Place validation operation was cancelled.");
        });*/
    }

    private Uni<ItemWithContext<Status>> persistPatch(ItemWithContext<WorkspaceResponse> iwc) {
        return Uni.createFrom().item((WorkspacePatch) iwc.context().get("request")).onItem()
                .transformToUni(req -> ppc.apply(req)
                        .map(status -> new ItemWithContext<>(iwc.context(),status))
                        .map(statusItemWithContext -> {
                            statusItemWithContext.context().put("response", iwc.get());
                            return statusItemWithContext;
                        }))
                .onTermination().invoke((itemWithContext,
                                         throwable,
                                         wasCancelled) -> {
                    if(!wasCancelled && throwable == null)
                        log.infof("Persisted patch for workspace %s from the database successfully.",
                                iwc.get().getWorkspace().getWid());
                    if(throwable != null)
                        log.errorf("Failed to persist patch to the database:", throwable);
                    if(wasCancelled)
                        log.infof("Patch persist operation was cancelled.");
                });
    }

    private Uni<Long> submitToNotification(ItemWithContext<Status> iwc) {
        return Uni.createFrom().item(iwc).onItem()
                .ifNotNull().transformToUni(itemWithContext -> {
                    if(itemWithContext.get().getStatus() != 200) {
                        return Uni.createFrom().item(500L);
                    }
                    Notification.Builder nb = itemWithContext.context().get("notification");
                    return ks.submit(nb.build()).onTermination().invoke((aLong, throwable, aBoolean) -> {
                        if(aLong == 200) {
                            log.infof("Notification successfully submitted to notification-service. users: [%s]",
                                    String.join(",",nb.getUsersList().stream().map(User::toString).toList()));
                        }
                    });
                })
                .onTermination().invoke((itemWithContext,
                                         throwable,
                                         wasCancelled) -> {
                    if(!wasCancelled && throwable == null)
                        log.infof("Notification submission terminated successfully.");
                    if(throwable != null)
                        log.errorf("Failed to submit notification to service:", throwable);
                    if(wasCancelled)
                        log.infof("Notification submission operation was cancelled.");
                });
    }

    private Uni<Long> submitToPlace(ItemWithContext<Status> iwc) {
        return Uni.createFrom().item(iwc).onItem()
                .ifNotNull().transformToUni(itemWithContext -> {
                    if(itemWithContext.get().getStatus() != 200) {
                        return Uni.createFrom().item(500L);
                    }

                    if(iwc.context().contains("google_place")){
                        WorkspacePatch wp = iwc.context().get("request");
                        APIResponse api = iwc.context().get("google_place");

                        return psud.store(UserPlace.newBuilder()
                                .setPlace(Place.newBuilder().setPlaceId(api.getResult().getPlace_id())
                                        .setRating(api.getResult().getRating())
                                        .addAllType(api.getResult().getTypes())
                                        .setGeometry(ProtoMapper.geometryMapper.serialize(api.getResult().getGeometry()))
                                        .build())
                                .setUser(User.newBuilder().setUserId(wp.getUser()).build())
                                .build()).onItem().transform(Status::getStatus)
                                .onTermination().invoke((aLong, throwable, aBoolean) -> {
                                    if(aLong == 200) {
                                        log.infof("User place successfully submitted to the database. [uid:%s,p_id:%s]",
                                                wp.getUser(),api.getResult().getPlace_id());
                                    }
                                });
                    }
                    return Uni.createFrom().item(200L);
                })
                .onTermination().invoke((itemWithContext,
                                         throwable,
                                         wasCancelled) -> {
                    if(!wasCancelled && throwable == null)
                        log.infof("Submit user location data to database terminated successfully.");
                    if(throwable != null)
                        log.errorf("Failed to submit location data to the database:", throwable);
                    if(wasCancelled)
                        log.infof("Location submit operation was cancelled.");
                });
    }

    private Uni<Void> endActions(ItemWithContext<Status> iwc) {
        return Uni.join().all(List.of(
                submitToNotification(iwc),submitToPlace(iwc))).andCollectFailures()
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transform(longs -> {
                    if(longs.get(0) == 200) {
                        log.info("Submitted successfully to Notification Service");
                    }
                    if(longs.get(1) == 200) {
                        log.info("Submitted successfully to Place Database");
                    }
                    return null;
                });
    }

    @Override
    public Uni<Status> apply(WorkspacePatch request) {

        return Uni.createFrom().item(request)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .log(String.format("Began processing request for workspace %s", request.getWorkspaceId()))
                .attachContext()
                // fetch workspace from database to validate if it exists (eventually would be the cache mechanism)
                .onItem().transformToUni(this::fetchWorkspaceFromDatabase)
                // validate location if it is a location
                .onItem().transformToUni(this::validateRequest)
                // persist the patch data in the database
                .onItem().transformToUni(this::persistPatch)
                // return value if successful
                .onTermination()
                //.call((statusItemWithContext, throwable, aBoolean) -> endActions(statusItemWithContext) )
                .call((statusItemWithContext, throwable, wasCancelled) -> {
                    if(!wasCancelled && throwable == null)
                        return submitToNotification(statusItemWithContext);
                    if(throwable != null)
                        log.warnf("Failed patch pipeline. Not emitting any notification %s", throwable.getMessage());
                    if(wasCancelled)
                        log.infof("Patch operation was cancelled.");
                    return null;
                })
                .onItem().transform(itemWithContext -> Status.newBuilder().setStatus(itemWithContext.get().getStatus()).build())
                .onFailure().recoverWithItem(() -> Status.newBuilder().setStatus(400).build());


        /*        ;

        return Uni.createFrom().item(request::hasLocation).onItem().transformToUni(hasLoc -> {
            if(hasLoc) { // if location return whether the location is valid or not, otherwise just return true
                log.infof("Patch request contains location %s", request.getLocation().getPlaceId());
                return loc_val.validate(request.getUser(), request.getLocation().getPlaceId());
            }
            log.info("Patch request does not contain location");
            return Uni.createFrom().item(true);
        }).ifNoItem().after(Duration.ofSeconds(5)).failWith(Exception::new)
        .onItem().transformToUni(isValid -> {
            log.infof("validity of Patch's contents: %s", isValid);
            if(isValid)
                return ppc.apply(request).log("Sent request over GRPC to db-service")
                        .onTermination()
                        .call((status, throwable, aBoolean) -> {
                            if (status.getStatus() == 200) {
                                return ks.submit(nb.setAction(request).build());
                            }
                            return Uni.createFrom().item(400);
                        }).log("Sent success")
                        .onFailure().invoke(throwable -> log.error("Error submitting notification to Kafka Object", throwable))
                        .onTermination().invoke(() -> log.infof("Successfully submitted notification for users of workspace [%s]",
                                String.join(" ,", nb.getUsersList().stream().map(AbstractMessage::toString).toList())));
            return Uni.createFrom().item(Status.newBuilder().setStatus(400).build());
        }).onFailure().invoke(throwable -> log.error(throwable));*/
    }
}
