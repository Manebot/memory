package io.manebot.plugin.memory;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.api.AudioConnection;
import io.manebot.plugin.audio.api.AudioRegistration;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.mixer.input.*;
import io.manebot.plugin.audio.mixer.output.PipedMixerSink;
import io.manebot.plugin.audio.mixer.output.RingBufferSink;
import io.manebot.plugin.audio.resample.FFmpegResampler;
import io.manebot.plugin.music.source.AudioProtocol;

import javax.sound.sampled.AudioFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Memorizer {
    private final Audio audio;

    /**
     * The audio channel being memorized
     */
    private final AudioChannel channel;

    /**
     * The static format of the entire processing chain
     */
    private final AudioFormat format;

    /**
     * The target ring buffer, master sink all samples go to
     */
    private final RingBufferSink sink;

    /**
     * The mixer we use to mix all providers (speakers; members) the parent channel will throw our way
     */
    private Mixer mixer;

    /**
     * The map of providers the channel is offering, to the virtual mixer channels we keep of them
     */
    private final Map<AudioProvider, MixerChannel> channelMap = new LinkedHashMap<>();

    /**
     * The pipe responsible for collecting audio from the channel being memorized
     */
    private final PipedMixerSink pipedMixerSink;
    private final SilentMixerChannel silentMixerChannel;

    private final MixerChannel loopbackPipe;

    private boolean registered = false;

    public Memorizer(Audio audio, AudioChannel channel, float seconds) {
        this.format = channel.getMixer().getAudioFormat();
        this.channel = channel;
        this.audio = audio;
        this.sink = new RingBufferSink(format, seconds);
        this.silentMixerChannel = new SilentMixerChannel(getFormat().getSampleRate(), getFormat().getChannels());
        this.pipedMixerSink = new PipedMixerSink(format, channel.getMixer().getBufferSize());
        this.loopbackPipe = new BasicMixerChannel(pipedMixerSink.getPipe());
    }

    public void onParentMixerStart() {
        mixer.removeChannel(loopbackPipe);
        mixer.removeChannel(silentMixerChannel);

        mixer.addChannel(loopbackPipe);

        mixer.setRunning(true);
    }

    public void onParentMixerStop() {
        mixer.removeChannel(loopbackPipe);
        mixer.removeChannel(silentMixerChannel);

        if (!mixer.isPlaying()) {
            silentMixerChannel.reset();
            mixer.addChannel(silentMixerChannel);
        }

        mixer.setRunning(true);
    }

    public float[] copyBuffer() {
        float[] src = sink.getBuffer();
        synchronized (src) {
            float[] copy = new float[src.length];
            System.arraycopy(src, 0, copy, 0, src.length);
            return copy;
        }
    }

    public boolean isRegistered() {
        return registered;
    }

    public boolean isRunning() {
        return mixer.isRunning();
    }

    public AudioChannel getChannel() {
        return channel;
    }

    public Mixer getMixer() {
        return mixer;
    }

    public Mixer getParentMixer() {
        return getChannel().getMixer();
    }

    public void register() {
        this.mixer = this.audio.createMixer(
                "memory:" + channel.getId(),
                (builder) -> {
                    builder.setFormat(channel.getMixer().getAudioSampleRate(), channel.getMixer().getAudioChannels());
                    builder.setBufferTime(channel.getMixer().getBufferSize() /
                            (channel.getMixer().getAudioChannels() * channel.getMixer().getAudioSampleRate()));
                    builder.addSink(sink);
                }
        );

        // Install silent channel when necessary
        if (!getChannel().getMixer().isPlaying())
            onParentMixerStop();
        else
            onParentMixerStart();

        AudioRegistration registration = audio.getRegistration(getChannel().getPlatform());
        if (registration == null) {
            throw new IllegalArgumentException("No registration on platform " + getChannel().getPlatform());
        }

        AudioConnection connection = registration.getConnection();
        if (connection == null) {
            throw new IllegalArgumentException("No connection on platform " + getChannel().getPlatform());
        }
        connection.registerMixer(mixer);

        mixer.setRunning(true);

        registered = true;
    }

    public void unregister() {
        this.mixer.empty();

        registered = false;
    }

    public void onUserBegin(AudioProvider provider) {
        final AudioProvider originalProvider = provider;

        if (provider.getFormat().getSampleRate() != mixer.getAudioSampleRate() ||
                provider.getChannels() != mixer.getAudioChannels()) {
            Logger.getGlobal().fine("Resampling provider " + provider.toString() + "...");

            int bufferSize = (int)mixer.getAudioSampleRate() * mixer.getAudioChannels();
            provider = new ResampledAudioProvider(provider, bufferSize, new FFmpegResampler(
                    provider.getFormat(),
                    mixer.getAudioFormat(),
                    bufferSize
            ));
        }

        MixerChannel mixerChannel = new BasicMixerChannel(provider);

        Logger.getGlobal().fine(
                "Installing Memorizer Channel " + mixerChannel.toString()
                + " on Mixer " + mixer.toString() + " for " + provider.toString() + "..."
        );

        if (this.mixer != null) {
            mixer.removeChannel(silentMixerChannel);
            mixer.addChannel(mixerChannel);
            mixer.setRunning(true);

            channelMap.put(originalProvider, mixerChannel);

            Logger.getGlobal().fine(
                    "Memorizer Channel " + mixerChannel.toString()
                    + " installed on Mixer " + mixer.toString()
                    + " for " + provider
            );
        }
    }

    public void onUserEnd(AudioProvider provider) {
        MixerChannel mixerChannel;
        if ((mixerChannel = channelMap.remove(provider)) != null && mixer != null) {
            mixer.removeChannel(mixerChannel);

            // If we don't do this, we will leak a pipe potentially
            try {
                mixerChannel.close();
            } catch (Exception e) {
                Logger.getGlobal().log(Level.WARNING, "Problem closing mixer channel for Memorizer", e);
            }

            Logger.getGlobal().fine("Memorizer Channel " + mixerChannel.toString()
                    + " uninstalled from Mixer " + mixer.toString()
                    + " for " + provider);

            if (!mixer.isPlaying()) {
                mixer.removeChannel(silentMixerChannel);
                silentMixerChannel.reset();
                mixer.addChannel(silentMixerChannel);
            }

            mixer.setRunning(true);
        }
    }

    public AudioFormat getFormat() {
        return format;
    }
}
