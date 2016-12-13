package org.freeticks;

import java.util.Iterator;
import java.util.stream.LongStream;

import static java.util.stream.LongStream.concat;

public interface Levels extends Iterable<Level>{
    // contains  not empty level by price
    boolean contains(long price);

    // aggregate volume of all orders at the get with price
    long volume(long price);

    // get level at price
    default Level get(long price) {
        return new Level(volume(price), price);
    }
}
