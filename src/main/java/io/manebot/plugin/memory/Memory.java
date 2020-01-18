package io.manebot.plugin.memory;

import io.manebot.plugin.*;

public class Memory implements PluginReference {
    private final Plugin plugin;
    private final Plugin audioPlugin;
    private final Plugin musicPlugin;

    public Memory(Plugin plugin, Plugin audioPlugin, Plugin musicPlugin) {
	this.plugin = plugin;
	this.audioPlugin = audioPlugin;
	this.musicPlugin = musicPlugin;
    }
    
    public Plugin getMemoryPlugin() {
        return plugin;
    }
    
    public Plugin getAudioPlugin() {
        return audioPlugin;
    }
    
    public Plugin getMusicPlugin() {
        return musicPlugin;
    }

    @Override
    public void load(Plugin.Future plugin) {
    
    }

    @Override
    public void unload(Plugin.Future plugin) {

    }
}
