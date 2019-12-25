package io.manebot.plugin.memory.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.*;
import io.manebot.plugin.music.database.model.*;

import javax.persistence.*;
import javax.persistence.Entity;

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

    public Track getTrack() {
	return track;
    }
}
