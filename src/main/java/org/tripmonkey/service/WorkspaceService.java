package org.tripmonkey.service;


import com.google.protobuf.Empty;
import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.tripmonkey.database.service.FetchWorkspaceClient;
import org.tripmonkey.database.service.PatchPersisterClient;
import org.tripmonkey.notification.service.Notification;
import org.tripmonkey.patch.data.Status;
import org.tripmonkey.patch.data.WorkspacePatch;
import org.tripmonkey.workspace.service.PatchApplier;
import org.tripmonkey.workspace.service.User;
import org.tripmonkey.workspace.service.WorkspaceRequest;
import org.tripmonkey.workspace.service.WorkspaceRequester;
import org.tripmonkey.workspace.service.WorkspaceResponse;

import java.util.ArrayList;

@GrpcService
public class WorkspaceService implements PatchApplier, WorkspaceRequester {

    @GrpcClient
    PatchPersisterClient ppc;

    @GrpcClient
    FetchWorkspaceClient fwc;

    // TODO Implement caching mechanism

    @Inject
    @Channel("notification-service")
    Emitter<Notification> em;

    @Override
    @RunOnVirtualThread
    public Uni<Status> apply(WorkspacePatch request) {

        Notification.Builder nb = Notification.newBuilder();
        
        fwc.fetch(WorkspaceRequest.newBuilder().setWid(request.getWorkspaceId()).build())
                .onItem().transform(workspaceResponse -> 
                {
                    if(workspaceResponse.hasWorkspace()){
                        return workspaceResponse.getWorkspace().getCollaboratorsList();
                    }
                    return new ArrayList<User>();
                }).subscribe().with(nb::addAllUsers);
        return ppc.apply(request).log("Sent request over GRPC to db-service")
                .onTermination()
                .call((status, throwable, aBoolean) ->
                        status.getStatus() == 200 ? Uni.createFrom().completionStage(em.send(nb.setAction(request).build())) :
                        Uni.createFrom().nothing())
                .log("Sent success");
    }

    @Override
    @RunOnVirtualThread
    public Uni<WorkspaceResponse> fetch(WorkspaceRequest request) {
        return fwc.fetch(request)
                .map(workspaceResponse -> workspaceResponse.hasWorkspace() ?
                        WorkspaceResponse.newBuilder().setWorkspace(workspaceResponse.getWorkspace()).build() :
                        WorkspaceResponse.newBuilder().build());
    }
}
