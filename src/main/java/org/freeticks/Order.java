package org.freeticks;

import java.time.Instant;

public interface Order extends Tick
{
    int evt();
    void setEvt(int value);

    long id();
    void setId(long value);

    long cookie();
    void setCookie(long cookie);

    long price();
    void setPrice(long price);

    long active();
    void setActive(long active);

    long filled();
    void setFilled(long filled);

    Instant serverTime();
    void setServerTime(Instant value);

    Instant clientTime();
    void setClientTIme(Instant value);
}

