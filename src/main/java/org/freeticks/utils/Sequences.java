package org.freeticks.utils;

import java.util.Iterator;
import java.util.stream.Stream;

public class Sequences {
    static final class StreamIterable<T> implements Iterable<T> {
        Stream<T> stream;
        StreamIterable(Stream<T> stream) {
            this.stream = stream;
        }
        @Override
        public Iterator<T> iterator() {
            return stream.iterator();
        }
    }

    public static final <T> Iterable<T> toIterable(Stream<T> stream) {
        return new StreamIterable<T>(stream);
    }
}
