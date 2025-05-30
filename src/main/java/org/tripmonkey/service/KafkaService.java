package org.tripmonkey.service;

import io.smallrye.mutiny.Uni;

import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.tripmonkey.notification.service.Notification;


@ApplicationScoped
public class KafkaService {

    @Inject
    @Channel("notification-service")
    MutinyEmitter<Notification> me;


    public Uni<Long> submit(Notification n){
        return me.send(n).map(unused -> 200L).onFailure().recoverWithItem(500L) ;
    }

}
