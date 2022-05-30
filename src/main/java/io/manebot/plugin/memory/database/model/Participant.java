package io.manebot.plugin.memory.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.TimedRow;
import io.manebot.database.model.User;

import javax.persistence.*;

public class Participant extends TimedRow {
    @Transient
    private final Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int participantId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "memoryId")
    private Memory memory;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private User user;

    public Participant(Database database) {
        this.database = database;
    }

    public Participant(Database database, Memory memory, User user) {
        this.database = database;
        this.memory = memory;
        this.user = user;
    }

    public Memory getMemory() {
        return memory;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}
