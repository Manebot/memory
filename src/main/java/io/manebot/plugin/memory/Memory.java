package io.manebot.plugin.memory;

import io.manebot.database.Database;
import io.manebot.event.EventHandler;
import io.manebot.event.EventListener;

import io.manebot.plugin.*;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.event.channel.AudioChannelUserBeginEvent;
import io.manebot.plugin.memory.database.model.MemoryManager;

import java.util.*;
import java.util.logging.Level;

public class Memory implements PluginReference, EventListener {
    private final Plugin plugin;
    private final Plugin audioPlugin;
    private final Plugin musicPlugin;

    private final MemoryManager memoryManager;

    private final Map<AudioChannel, Memorizer> memorizerMap = new HashMap<>();

    private float seconds;

    public Memory(Plugin plugin, Plugin audioPlugin, Plugin musicPlugin, Database memoryDatabase) {
        this.plugin = plugin;
        this.audioPlugin = audioPlugin;
        this.musicPlugin = musicPlugin;

        this.memoryManager = new MemoryManager(memoryDatabase);
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

    public MemoryManager getMemoryManager() {
        return memoryManager;
    }

    @Override
    public void load(Plugin.Future future) {
        seconds = Float.parseFloat(future.getPlugin().getProperty("memorySeconds", "30"));
    }

    @Override
    public void unload(Plugin.Future future) {
        synchronized (memorizerMap) {
            Set<AudioChannel> registeredChannels = new HashSet<>(memorizerMap.keySet());
            registeredChannels.forEach(channel -> {
                Memorizer removed = memorizerMap.remove(channel);
                if (removed == null) {
                    return;
                }

                removed.unregister();
            });
        }
    }

    private Memorizer getMemorizer(AudioChannel channel) {
        return memorizerMap.computeIfAbsent(channel, (ch) -> {
            channel.getConversation().checkPermission("memory.listen");
            Memorizer m = new Memorizer(audioPlugin.getInstance(Audio.class), ch, seconds);
            m.register();
            return m;
        });
    }

    @EventHandler()
    public void onUserBegin(AudioChannelUserBeginEvent userBeginEvent) {
        try {
            synchronized (memorizerMap) {
                getMemorizer(userBeginEvent.getChannel()).onUserBegin(userBeginEvent.getProvider());
            }
        } catch (SecurityException ex) {
            plugin.getLogger().log(Level.FINE, "Security exception encountered when setting up mixer", ex);
        }
    }

    @EventHandler()
    public void onUserEnd(AudioChannelUserBeginEvent userEndEvent) {
        try {
            synchronized (memorizerMap) {
                getMemorizer(userEndEvent.getChannel()).onUserEnd(userEndEvent.getProvider());
            }
        } catch (SecurityException ex) {
            plugin.getLogger().log(Level.FINE, "Security exception encountered when setting up mixer", ex);
        }
    }
}
