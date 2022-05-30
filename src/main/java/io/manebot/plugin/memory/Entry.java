package io.manebot.plugin.memory;

import io.manebot.artifact.*;
import io.manebot.database.Database;
import io.manebot.plugin.*;
import io.manebot.plugin.java.*;
import io.manebot.plugin.memory.database.model.*;

public class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginLoadException {
        builder.setType(PluginType.FEATURE);
    
        Plugin audioPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));
        Plugin musicPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:music"));

        Database memoryDatabase = builder.addDatabase("memory", (modelConstructor) -> {
            modelConstructor.addDependency(modelConstructor.getSystemDatabase());
            modelConstructor.addDependency(musicPlugin.getDatabase("music"));
            modelConstructor.registerEntity(Memory.class);
            modelConstructor.registerEntity(Participant.class);
        });

        builder.setInstance(Memory.class, (plugin) -> new Memory(plugin, audioPlugin, musicPlugin, memoryDatabase));
        builder.addListener(future -> future.getPlugin().getInstance(Memory.class));
    }
}