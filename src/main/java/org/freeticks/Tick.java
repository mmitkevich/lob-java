package org.freeticks;

import java.time.Instant;

public interface Tick
{
    int evt();
    long id();
    long cookie();
    long flags();
    Instant serverTime();
    Instant clientTime();
}
