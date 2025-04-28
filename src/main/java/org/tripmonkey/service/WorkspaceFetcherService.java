package org.tripmonkey.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import org.tripmonkey.database.service.FetchWorkspace;
import org.tripmonkey.workspace.service.WorkspaceRequest;
import org.tripmonkey.workspace.service.WorkspaceRequester;
import org.tripmonkey.workspace.service.WorkspaceResponse;

@GrpcService
public class WorkspaceFetcherService implements WorkspaceRequester {

    @GrpcClient("fwc")
    FetchWorkspace fwc;

    @Override
    public Uni<WorkspaceResponse> fetch(WorkspaceRequest request) {
        return fwc.fetch(request)
                .map(workspaceResponse -> workspaceResponse.hasWorkspace() ?
                        WorkspaceResponse.newBuilder().setWorkspace(workspaceResponse.getWorkspace()).build() :
                        WorkspaceResponse.newBuilder().build());
    }
}
