package dev.tirthrajsinh.servicepulse.notification;

public interface NotificationDeliveryAdapter {

    void deliver(NotificationJob job);
}
