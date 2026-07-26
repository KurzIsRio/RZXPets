package me.rzx.core.api;

import me.rzx.core.event.RZXEvent;
import me.rzx.core.plugin.PluginService;
import me.rzx.core.plugin.PluginRecord;

public class RZXCoreAPI {
    private static final Registry registry = new Registry();
    private static final Bus bus = new Bus();

    public static boolean isReady() {
        return org.bukkit.Bukkit.getPluginManager().isPluginEnabled("RZXCore");
    }

    public static Registry getRegistry() {
        return registry;
    }

    public static Bus getBus() {
        return bus;
    }

    public static class Registry {
        @SuppressWarnings("unchecked")
        public <T> T get(Class<T> type) {
            if (type == PluginService.class) {
                return (T) new PluginService() {
                    @Override
                    public void registerPlugin(PluginRecord record) {}
                };
            }
            return null;
        }
    }

    public static class Bus {
        public void publish(RZXEvent event) {}
    }
}
