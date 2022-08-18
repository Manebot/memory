package io.manebot.plugin.memory;

import io.manebot.artifact.*;
import io.manebot.database.Database;
import io.manebot.plugin.*;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AudioRegistration;
import io.manebot.plugin.java.*;
import io.manebot.plugin.memory.command.MemoryCommand;
import io.manebot.plugin.memory.database.model.*;

public class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginLoadException {
        builder.setType(PluginType.FEATURE);
    
        Plugin audioPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));
        Plugin musicPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:music"));

        Database memoryDatabase = builder.addDatabase("memory", (modelConstructor) -> {
            modelConstructor.addDependency(musicPlugin.getDatabases().stream()
                    .findFirst().orElseThrow());
            modelConstructor.registerEntity(io.manebot.plugin.memory.database.model.Memory.class);
            modelConstructor.registerEntity(Participant.class);
        });

        builder.setInstance(Memory.class, (plugin) -> {
            return new Memory(plugin, audioPlugin, musicPlugin, memoryDatabase);
        });

        builder.addListener(future -> future.getPlugin().getInstance(Memory.class));

        builder.addCommand("memory", (future) -> new MemoryCommand(future, audioPlugin, musicPlugin));
    }
}