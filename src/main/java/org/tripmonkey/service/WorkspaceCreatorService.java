package org.tripmonkey.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.tripmonkey.database.service.CreateWorkspace;
import org.tripmonkey.domain.data.Workspace;
import org.tripmonkey.proto.domain.ProtoMapper;
import org.tripmonkey.workspace.service.User;
import org.tripmonkey.workspace.service.WorkspaceCreator;
import org.tripmonkey.workspace.service.WorkspaceResponse;

@GrpcService
public class WorkspaceCreatorService implements WorkspaceCreator {

    @Inject
    Logger log;

    @GrpcClient("cwk")
    CreateWorkspace cwk;

    @Override
    public Uni<WorkspaceResponse> create(User request) {
        log.infof("Received request to create! %s", request.getUserId());
        return Uni.createFrom().item(ProtoMapper.userMapper.deserialize(request))
                .log(String.format("Received request to create workspace for user %s", request.getUserId()))
                .map(Workspace::createFor)
                .map(ProtoMapper.workspaceMapper::serialize).onItem().transformToUni(cwk::create);
    }
}
