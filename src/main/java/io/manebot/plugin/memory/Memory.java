package io.manebot.plugin.memory;

import io.manebot.plugin.*;

public class Memory implements PluginReference {
    private final Plugin plugin;

    public Memory(Plugin plugin) {
	this.plugin = plugin;
    }

    @Override
    public void load(Plugin.Future plugin) {

    }

    @Override
    public void unload(Plugin.Future plugin) {

    }
}
