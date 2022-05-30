package io.manebot.plugin.memory.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.*;
import io.manebot.plugin.music.database.model.*;

import javax.persistence.*;
import javax.persistence.Entity;
import java.util.Collection;
import java.util.Collections;

@Entity
@Table
public class Memory extends TimedRow {
    @Transient
    private final Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int memoryId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trackId")
    private Track track;

    public Memory(Database database) {
        this.database = database;
    }

    public Memory(Database database, Track track) {
        this.database = database;
        this.track = track;
    }

    private int getMemoryId() {
        return memoryId;
    }

    /**
     * Gets the track associated with this memory.
     * @return track.
     */
    public Track getTrack() {
        return track;
    }

    /**
     * Gets an unmodifiable collections of participants of this memory.
     * @return unmodifiable collection of participants.
     */
    public Collection<Participant> getParticipants() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Participant.class.getName() + " x where x.memoryId = :memoryId",
                    Participant.class
            ).setParameter("memoryId", getMemoryId()).getResultList();
        }));
    }
}
