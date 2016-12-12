package org.freeticks;

public interface Order
{
    OrderBook.Event evt();
    long id();
    long cookie();
    long price();
    long active();
    long filled();
    long serverTime();
    long clientTime();
}
