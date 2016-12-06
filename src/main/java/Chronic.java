import net.openhft.chronicle.queue.*;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.impl.single.SingleChronicleQueueBuilder;
import net.openhft.chronicle.wire.DocumentContext;

import static java.awt.SystemColor.text;

/**
 * Created by mike on 05.12.16.
 */
public class Chronic {
    public static void main(String[] args) {
        String basePath = "/home/mike/dev/java/perftests/data";
        ChronicleQueue queue = SingleChronicleQueueBuilder.binary(basePath).build();
        ExcerptAppender appender = queue.acquireAppender(); // sequential writes.
        ExcerptTailer tailer = queue.createTailer();       // sequential reads ideally, but random reads/write also possible.
        try (final DocumentContext dc = appender.writingDocument()) {
            dc.wire().write().text("EURUSD").writeLong(123);
            System.out.println("your data was store to index="+ dc.index());
        }
    }
}
