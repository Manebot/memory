package io.manebot.plugin.memory;

import io.manebot.database.Database;
import io.manebot.event.EventHandler;
import io.manebot.event.EventListener;

import io.manebot.plugin.*;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AudioRegistration;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.event.channel.AudioChannelUserBeginEvent;
import io.manebot.plugin.audio.event.channel.AudioChannelUserEndEvent;
import io.manebot.plugin.audio.event.mixer.MixerStateChangedEvent;
import io.manebot.plugin.audio.mixer.Mixer;
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

    public Memorizer getMemorizer(AudioChannel channel) {
        if (channel == null) {
            return null;
        }

        return memorizerMap.computeIfAbsent(channel, (ch) -> {
            channel.getConversation().checkPermission("memory.listen");

            Audio audio = audioPlugin.getInstance(Audio.class);
            Memorizer m = new Memorizer(audio, ch, seconds);
            m.register();
            return m;
        });
    }

    public Memorizer getMemorizer(Mixer mixer) {
        if (mixer == null) {
            return null;
        }

        return memorizerMap.values().stream()
                .filter(m -> m.getParentMixer() == mixer)
                .findFirst().orElse(null);
    }

    @EventHandler()
    public void onUserBegin(AudioChannelUserBeginEvent userBeginEvent) {
        try {
            synchronized (memorizerMap) {
                Memorizer memorizer = getMemorizer(userBeginEvent.getChannel());
                if (memorizer == null)
                    return;
                memorizer.onUserBegin(userBeginEvent.getProvider());
            }
        } catch (SecurityException ex) {
            plugin.getLogger().log(Level.FINE, "Security exception encountered when setting up mixer", ex);
        }
    }

    @EventHandler()
    public void onUserEnd(AudioChannelUserEndEvent userEndEvent) {
        try {
            synchronized (memorizerMap) {
                Memorizer memorizer = getMemorizer(userEndEvent.getChannel());
                if (memorizer == null)
                    return;
                memorizer.onUserEnd(userEndEvent.getProvider());
            }
        } catch (SecurityException ex) {
            plugin.getLogger().log(Level.FINE, "Security exception encountered when setting up mixer", ex);
        }
    }

    @EventHandler()
    public void onStateChanged(MixerStateChangedEvent stateChangedEvent) {
        try {
            Mixer mixer = stateChangedEvent.getMixer();

            synchronized (memorizerMap) {
                Memorizer memorizer = getMemorizer(mixer);
                if (memorizer == null) {
                    return;
                }

                if (mixer.isPlaying()) {
                    memorizer.onParentMixerStart();
                } else {
                    memorizer.onParentMixerStop();
                }
            }
        } catch (SecurityException ex) {
            plugin.getLogger().log(Level.FINE, "Security exception encountered when setting up mixer", ex);
        }
    }
}
