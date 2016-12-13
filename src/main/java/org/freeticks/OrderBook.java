package org.freeticks;

import java.util.stream.LongStream;

import static org.freeticks.OrderType.*;

public interface OrderBook {
    long NO_BID = -Long.MAX_VALUE;
    long NO_ASK = Long.MAX_VALUE;

    // get levels in the book
    Levels levels();

    // bid prices
    LongStream bids();

    // offer prices
    LongStream asks();

    // orders at price
    LongStream orders(long price);

    // lowest price
    long low();

    // highest price
    long high();

    // best bid price
    long bid();

    // best offer price
    long ask();

    // head order id by price
    long head(long price);

    // next order id by previous order id
    long next(long id);

    default boolean hasBids() { return bid()!=NO_BID; }

    default boolean hasAsks() { return ask()!=NO_ASK; }

    // place order, return id
    long place(long volume, long price, long cookie, long goodTill);

    // cancel order, return volume left at price get
    long cancel(long id, long cookie);

    default long  place(long volume, long price, long cookie) {
        return place(volume, price, cookie, GTC);
    }

}
