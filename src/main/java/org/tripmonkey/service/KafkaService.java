package org.tripmonkey.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Outgoing;
import org.tripmonkey.notification.service.Notification;

@ApplicationScoped
public class KafkaService {

    @Inject
    @Channel("notification-service")
    Emitter<Notification> em;

}
