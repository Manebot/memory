package io.manebot.plugin.memory;

import io.manebot.artifact.*;
import io.manebot.plugin.*;
import io.manebot.plugin.java.*;
import io.manebot.plugin.memory.database.model.*;

public class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginLoadException {
        builder.setType(PluginType.FEATURE);

        Plugin musicPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:music"));

        builder.addDatabase("memory", (modelConstructor) -> {
            modelConstructor.addDependency(modelConstructor.getSystemDatabase());
            modelConstructor.addDependency(musicPlugin.getDatabase("music"));
            modelConstructor.registerEntity(Memory.class);
        });

        builder.setInstance(Memory.class, (plugin) -> new Memory(plugin));
    }
}