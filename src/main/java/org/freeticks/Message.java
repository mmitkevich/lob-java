package org.freeticks;

import java.time.Instant;

public class Message implements Tick {
    int evt;
    long id;
    long cookie;
    long flags;
    long serverTime;
    long serverTimeNanos;
    long clientTime;
    long clientTimeNanos;

    public static long lastId = 0;

    public Message() { }

    public Message(int evt) {
        this.id = ++lastId;
        this.evt = evt;
    }

    public Message(int evt, long id) {
        this(evt);
        this.id = id;
    }

    @Override
    public int evt() {
        return evt;
    }

    public void setEvt(int value) {
        this.evt = evt;
    }

    @Override
    public long id() {
        return id;
    }

    public void setId(long value) {
        this.id = value;
    }

    @Override
    public Instant serverTime() {
        return Instant.ofEpochSecond(serverTime, serverTimeNanos);
    }

    public void setServerTime(Instant value) {
        this.serverTime = value.getEpochSecond();
        this.serverTimeNanos = value.getNano();
    }

    @Override
    public Instant clientTime() {
        return Instant.ofEpochSecond(clientTime, clientTimeNanos);
    }

    public void setClientTIme(Instant value) {
        this.clientTime = value.getEpochSecond();
        this.clientTimeNanos = value.getNano();
    }

    @Override
    public long cookie() {
        return cookie;
    }

    public void setCookie(long value) {
        this.cookie = value;
    }

    @Override
    public long flags() {
        return flags;
    }

    public void setFlags(long value) {
        this.flags = value;
    }
}
