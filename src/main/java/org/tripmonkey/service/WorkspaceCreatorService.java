package org.tripmonkey.service;

import io.quarkus.grpc.GrpcClient;
import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import org.tripmonkey.database.service.CreateWorkspace;
import org.tripmonkey.domain.data.Workspace;
import org.tripmonkey.proto.ProtoSerde;
import org.tripmonkey.workspace.service.User;
import org.tripmonkey.workspace.service.WorkspaceCreator;
import org.tripmonkey.workspace.service.WorkspaceResponse;

@GrpcService
public class WorkspaceCreatorService implements WorkspaceCreator {

    @GrpcClient("cwk")
    CreateWorkspace cwk;

    @Override
    public Uni<WorkspaceResponse> create(User request) {
        return Uni.createFrom().item(ProtoSerde.userMapper.deserialize(request))
                .map(Workspace::createFor)
                .map(ProtoSerde.workspaceMapper::serialize).onItem().transformToUni(cwk::create);
    }
}
