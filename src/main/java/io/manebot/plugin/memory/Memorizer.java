package io.manebot.plugin.memory;

import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.Mixer;
import io.manebot.plugin.audio.mixer.input.AudioProvider;
import io.manebot.plugin.audio.mixer.input.BasicMixerChannel;
import io.manebot.plugin.audio.mixer.input.MixerChannel;
import io.manebot.plugin.audio.mixer.input.SilentMixerChannel;
import io.manebot.plugin.audio.mixer.output.PipedMixerSink;
import io.manebot.plugin.audio.mixer.output.RingBufferSink;

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

    public Memorizer(Audio audio, AudioChannel channel, float seconds) {
        this.format = channel.getMixer().getAudioFormat();
        this.channel = channel;
        this.audio = audio;
        this.sink = new RingBufferSink(format, seconds);
        this.silentMixerChannel = new SilentMixerChannel(getFormat().getSampleRate(), getFormat().getChannels());
        this.pipedMixerSink = new PipedMixerSink(format, channel.getMixer().getBufferSize());
        this.loopbackPipe = new BasicMixerChannel(pipedMixerSink.getPipe());
    }

    private void onParentMixerStart() {
        mixer.addChannel(new BasicMixerChannel(loopbackPipe));
        mixer.removeChannel(silentMixerChannel);
    }

    private void onParentMixerStop() {
        mixer.addChannel(loopbackPipe);
        mixer.removeChannel(silentMixerChannel);
    }

    public float[] getBuffer() {
        return sink.getBuffer();
    }

    public boolean isRunning() {
        return mixer.isRunning();
    }

    public AudioChannel getChannel() {
        return channel;
    }

    public void register() {
        this.mixer = this.audio.createMixer(
                "memory:" + channel.getId(),
                (builder) -> {
                    builder.setFormat(channel.getMixer().getAudioSampleRate(), channel.getMixer().getAudioChannels());
                    builder.setBufferTime(channel.getMixer().getBufferSize() /
                            (channel.getMixer().getAudioChannels() * channel.getMixer().getAudioSampleRate()));
                    builder.addSink(pipedMixerSink);
                }
        );

        // Install silent channel when necessary
        if (!getChannel().getMixer().isRunning())
            onParentMixerStop();
    }

    public void unregister() {
        this.mixer.empty();
    }

    public void onUserBegin(AudioProvider provider) {
        MixerChannel mixerChannel = new BasicMixerChannel(provider);

        Logger.getGlobal().fine(
                "Installing Memorizer Channel " + mixerChannel.toString()
                + " on Mixer " + mixer.toString() + " for " + provider.toString() + "..."
        );

        if (this.mixer != null) {
            mixer.addChannel(mixerChannel);

            channelMap.put(provider, mixerChannel);

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
        }
    }

    public AudioFormat getFormat() {
        return format;
    }
}
