package com.sena.notification_service.port.in;

public interface ConsumeDomainEventUseCase {
    void handle(ConsumeDomainEventCommand command);
}
