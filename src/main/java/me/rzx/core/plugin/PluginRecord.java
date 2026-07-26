package me.rzx.core.plugin;

import java.util.List;
import me.rzx.core.lifecycle.PluginState;

public class PluginRecord {
    public PluginRecord(
        String name,
        String version,
        int api,
        String[] authors,
        long loadTime,
        List<String> services,
        List<String> commands,
        List<String> dependencies,
        PluginState state,
        String[] requiredPlugins
    ) {}
}
