package org.freeticks;
import net.openhft.affinity.AffinityLock;
import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.bytes.NativeBytes;
import net.openhft.chronicle.core.Jvm;
import net.openhft.chronicle.queue.*;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.WireType;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

import static net.openhft.chronicle.queue.RollCycles.SMALL_DAILY;

public class ChronicleQueueTest {
    private static final int BYTES_LENGTH = 256;
    private static final int BLOCK_SIZE = 256 << 20;
    private static final long INTERVAL_US = 0; //10;
    private static final boolean buffered = true;

    public void testTwoThreads() throws InterruptedException{
        String path = "deleteme.q";
        new File(path).deleteOnExit();

        AtomicLong counter = new AtomicLong();

        Thread tailerThread = new Thread(() -> {
            AffinityLock rlock = AffinityLock.acquireLock();
            Bytes bytes = NativeBytes.nativeBytes(BYTES_LENGTH).unchecked(true);
            try (ChronicleQueue rqueue = new SingleChronicleQueueBuilder(path)
                    .wireType(WireType.FIELDLESS_BINARY)
                    .blockSize(BLOCK_SIZE)
                    .build()) {

                ExcerptTailer tailer = rqueue.createTailer();

                long sum = 0;
                long value;
                while (!Thread.interrupted()) {
                    bytes.clear();
                    if (tailer.readBytes(bytes)) {
                        value = bytes.readLong();
                        if(value == -1)
                            break;
                        sum += value;
                        counter.incrementAndGet();
                    }
                }
                System.out.printf("sum = %d", sum);
            } finally {
                if (rlock != null) {
                    rlock.release();
                }
                System.out.printf("Read %,d messages", counter.intValue());
            }
        }, "tailer thread");

        long runs = 50_000;

        Thread appenderThread = new Thread(() -> {
            AffinityLock wlock = AffinityLock.acquireLock();
            try {
                ChronicleQueue wqueue = SingleChronicleQueueBuilder.binary(path)
                        .wireType(WireType.FIELDLESS_BINARY)
                        .rollCycle(SMALL_DAILY)
                        .blockSize(BLOCK_SIZE)
                        .buffered(buffered)
                        .build();

                ExcerptAppender appender = wqueue.acquireAppender();

                long start = System.nanoTime();
                int i=0;
                for ( i = 0; i < runs; i++) {
                    appender.writeDocument(wire ->
                            wire.write().int8(0));
                }
                appender.writingDocument(i).wire().write().int64(-1);

                wqueue.close();
                System.out.printf("Written %d items", runs);
            } finally {
                if (wlock != null) {
                    wlock.release();
                }
            }
        }, "appender thread");

        tailerThread.start();
        Jvm.pause(100);

        appenderThread.start();
        appenderThread.join();

        //Pause to allow tailer to catch up (if needed)
        for (int i = 0; i < 10; i++) {
            if (runs != counter.get())
                Jvm.pause(Jvm.isDebug() ? 10000 : 100);
        }

        for (int i = 0; i < 10; i++) {
            tailerThread.interrupt();
            tailerThread.join(100);
        }

        //assertEquals(runs, counter.get());
    }

    public static void main(String[] args) {
        try {
            ChronicleQueueTest test = new ChronicleQueueTest();
            test.testTwoThreads();
        }catch(Throwable t) {
            System.out.println(t);
        }
    }
}
