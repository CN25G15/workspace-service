package org.tripmonkey.proto.kafka;


import org.apache.kafka.common.serialization.Serializer;
import org.tripmonkey.notification.service.Notification;

public class NotificationSerializer implements Serializer<Notification> {

    public byte[] serialize(String topic, Notification notif) {
        return notif.toByteArray();
    }

}
