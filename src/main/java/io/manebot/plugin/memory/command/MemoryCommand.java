package io.manebot.plugin.memory.command;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.database.model.User;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginRegistration;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.audio.mixer.output.AudioConsumer;
import io.manebot.plugin.memory.Memorizer;
import io.manebot.plugin.memory.Memory;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.config.AudioDownloadFormat;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.database.model.TrackRepository;
import io.manebot.plugin.music.repository.NullRepository;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.plugin.music.source.AudioProtocol;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.UUID;

public class MemoryCommand extends AnnotatedCommandExecutor {
    private final PluginRegistration pluginRegistration;
    private final Plugin audioPlugin;
    private final Plugin musicPlugin;

    public MemoryCommand(Plugin.Future future, Plugin audioPlugin, Plugin musicPlugin) {
        this.pluginRegistration = future.getRegistration();
        this.audioPlugin = audioPlugin;
        this.musicPlugin = musicPlugin;
    }

    private Memory getMemory() {
        return pluginRegistration.getInstance().getInstance(Memory.class);
    }

    private Audio getAudio() {
        return audioPlugin.getInstance(Audio.class);
    }

    private Music getMusic() {
        return musicPlugin.getInstance(Music.class);
    }

    private Memorizer getMemorizer(CommandSender sender) throws CommandArgumentException {
        Memory memory = getMemory();
        Audio audio = getAudio();
        AudioChannel channel = audio.getChannel(sender);
        if (channel == null) {
            throw new CommandArgumentException("There is no audio channel associated with this conversation.");
        }

        return memory.getMemorizer(channel);
    }

    @Command(description = "Captures a memory of the audio channel", permission = "memory.save")
    public void save(CommandSender sender) throws Exception {
        Memorizer memorizer = getMemorizer(sender);
        AudioChannel channel = memorizer.getChannel();

        Music music = getMusic();
        Community community = music.getCommunity(memorizer.getChannel().getConversation());
        if (community == null) {
            throw new CommandArgumentException("There is no music community associated with this conversation.");
        }

        TrackRepository trackRepository = community.getRepository();
        if (trackRepository == null) {
            throw new CommandArgumentException("There is no track repository associated with this music community.");
        } else if (trackRepository.getInstance() instanceof NullRepository) {
            throw new CommandArgumentException("This music community does not support saving new tracks.");
        }

        Track memoryTrack;
        try (AudioChannel.Ownership ownership = channel.obtainChannel(sender.getPlatformUser().getAssociation())) {
            memoryTrack = save((User) sender.getUser(), memorizer, community);
        }

        sender.sendMessage("Memory saved as \"" + memoryTrack.getName() + "\".");
    }

    private Track save(User user, Memorizer memorizer, Community community) throws CommandArgumentException, MalformedURLException {
        if (!memorizer.isRunning())
            throw new CommandArgumentException("Memorizer is not running.");

        float sampleRate = memorizer.getMixer().getAudioSampleRate();
        int channels = memorizer.getMixer().getAudioChannels();

        // Copy the memorizer buffer
        float[] buffer = memorizer.copyBuffer();
        if (getTimeInSeconds(buffer, sampleRate, channels) < 1F)
            throw new CommandArgumentException("Ring buffer too small.");

        buffer = trim(buffer, channels);
        float time = getTimeInSeconds(buffer, sampleRate, channels);
        if (time < 1F)
            throw new CommandArgumentException("There is nothing to remember.");

        return save(user, buffer, community, time);
    }

    private Track save(User user, float[] buffer, Community community, float seconds) throws CommandArgumentException,
            MalformedURLException {
        URL url = URI.create("file:/dev/null?memory=1&community=" +
                URLEncoder.encode(community.getName())
                + "&time=" + System.currentTimeMillis()).toURL();

        UUID uuid = Repository.toUUID(url);

        Repository.Resource resource;
        try {
            resource = community.getRepository().getInstance().get(uuid);
        } catch (IOException e) {
            throw new CommandArgumentException("Problem generating resource with uuid=" + uuid, e);
        }

        if (resource.exists())
            throw new CommandArgumentException("Resource already exists.");

        AudioProtocol protocol = getMusic().getProtocol();
        AudioDownloadFormat format = community.getRepository().getFormat();
        try (AudioConsumer consumer = protocol.openConsumer(resource.openWrite(), format)) {
            consumer.write(buffer, buffer.length);
        } catch (Exception ex) {
            throw new CommandArgumentException("Problem saving memory to file", ex);
        }

        // Create the track file locally
        community.getRepository().createFile(community.getRepository(), uuid, format.getContainerFormat());

        Date date = Calendar.getInstance().getTime();
        DateFormat dateFormat = new SimpleDateFormat("yyyy-mm-dd hh:mm:ss");
        String strDate = dateFormat.format(date);

        // Create a new track
        return community.getOrCreateTrack(url, (builder) -> {
            builder.setLength((double) seconds);
            builder.setName(community.getName() + " memory from " + strDate);
            builder.setUser(user);
            builder.setUrl(url);

            builder.addTag("memory");
            builder.addTag(community.getName());
        });
    }

    /**
     * Trims an audio buffer, removing any silence in the signal from the "left" (start) or "right" (end)
     * @param buffer buffer to trim silence from
     * @param channels number of channels in the source audio stream
     * @return trimmed audio buffer in the same format as the input.
     */
    private float[] trim(float[] buffer, int channels) {
        int start = 0, end = 0;
        int totalFrames = buffer.length / channels;

        for (int frame = 0; frame < totalFrames; frame += 1) {
            int sampleStart = frame * channels;
            boolean noise = false;
            for (int sampleIndex = 0; sampleIndex < channels; sampleIndex ++) {
                float sample = buffer[sampleStart + sampleIndex];
                if (sample != 0f) {
                    noise = true;
                    break;
                }
            }

            if (noise) {
                end = frame;
                if (start <= 0) {
                    start = frame;
                }
            }
        }

        int frameLength = end - start;
        int sampleLength = frameLength * channels;
        float[] trimmed = new float[sampleLength];

        System.arraycopy(buffer, start * channels, trimmed, 0, sampleLength);

        return trimmed;
    }

    private float getTimeInSeconds(float[] buffer, float sampleRate, int channels) {
        return (float)buffer.length / (sampleRate * channels);
    }

    @Command
    public void debugInfo(CommandSender sender, @CommandArgumentLabel.Argument(label = "debug-info") String label)
            throws CommandExecutionException {
        Memorizer memorizer = getMemorizer(sender);
        AudioChannel channel = memorizer.getChannel();

        sender.sendDetails(builder -> {
            builder.name("Channel").key(channel.getId());
            builder.name("Mixer").key(memorizer.getMixer().getId());
            builder.item("Format", memorizer.getFormat());
            builder.item("Running", memorizer.isRunning());
            builder.item("Registered", memorizer.isRegistered());
        });
    }

}
