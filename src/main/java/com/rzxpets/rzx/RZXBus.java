package com.rzxpets.rzx;

import me.rzx.core.api.RZXCoreAPI;
import me.rzx.core.event.RZXEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class RZXBus {
    private static final List<Consumer<Object>> listeners = new ArrayList<>();

    public static synchronized void subscribe(Consumer<Object> listener) {
        listeners.add(listener);
    }

    public static void publish(Object event) {
        if (event == null) return;
        
        // Notify local listeners
        for (Consumer<Object> listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                RZXExceptionHandler.handle("RZXBus Event Dispatch", e);
            }
        }

        // Forward to RZXCore API global bus if event is an RZXEvent
        try {
            if (event instanceof RZXEvent rzxEvent && RZXCoreAPI.isReady()) {
                RZXCoreAPI.getBus().publish(rzxEvent);
            }
        } catch (Throwable ignored) {}
    }
}
