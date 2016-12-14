package org.freeticks;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.freeticks.OrderEvent.*;

import com.google.common.collect.Iterables;
import org.freeticks.lob.OffHeapBook;
import org.freeticks.lob.OrderEmitter;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.JUnitCore;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.TimeValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import pl.wavesoftware.jmh.junit.utilities.JavaAgentSkip;
import pl.wavesoftware.jmh.junit.utilities.JmhCleaner;


public class OrderBookTest
{
    protected static final Logger LOG = LoggerFactory.getLogger(OrderBookTest.class);
    static final boolean NO_LOGS = true;


    static {
        System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
    }

    @ClassRule
    public static RuleChain chain = RuleChain
            .outerRule(new JmhCleaner(OrderBookTest.class))
            .around(JavaAgentSkip.ifPresent());

    OffHeapBook book(ArrayList<OrderMessage> events) {
        OffHeapBook book = OffHeapBook.builder()
                .range(-10000,10000)
                .onEvent((evt, filled, active, price, id, cookie) -> events.add(new OrderMessage(evt,filled,active,price,id,cookie)))
                .onError((evt, id, cookie) -> events.add(new OrderMessage(evt,0,0,0,id,cookie)))
                .build();
        return book;
    }

    @Test
    public void place() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();

        long bid100 = book.place(10L,     100L,   555L);

        assertThat(events).flatExtracting(
                                Order::evt, Order::active, Order::filled,   Order::price,     Order::cookie).containsExactly(
                                PLACE,      (long)10,      (long)0,         (long)100,        (long)555);

        events.clear();

        long ask101 = book.place(-10,    101,   666);

        assertThat(events).flatExtracting(
                                Order::evt, Order::active,  Order::filled,  Order::price,   Order::cookie).containsExactly(
                                PLACE,      (long)-10,      (long) 0,       (long) 101,     (long)666);

        LOG.info(book.toString());

        assertThat(book.bid()).isEqualTo(100);
        assertThat(book.ask()).isEqualTo(101);
        assertThat(book.levels().volume(book.bid())).isEqualTo(10);
        assertThat(book.levels().volume(book.ask())).isEqualTo(-10);
        Level[] lvls = Iterables.toArray(book.levels(),Level.class);
        assertThat(lvls).containsExactly(new Level(10, 100), new Level(-10, 101));
    }


    @Test
    public void cancel() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();

        long bid100 = book.place(10,     100, 555);
        assertThat(book.bid()).isEqualTo(100);

        long ask101 = book.place(-10,    101, 666);
        assertThat(book.ask()).isEqualTo(101);

        events.clear();
        book.cancel(bid100, 555);
        assertThat(events).flatExtracting(
                Order::evt,   Order::active,    Order::filled,    Order::price,     Order::cookie,    Order::id).containsExactly(
                CANCEL,         (long)10,       (long)0,            (long)100,      (long)555,      (long)bid100);
        assertThat(book.bid()).isEqualTo(OrderBook.NO_BID);
        assertThat(book.ask()).isEqualTo(101);
        assertThat(book.levels().volume(book.ask())).isEqualTo(-10);
        assertThat(book.low()).isEqualTo(101);
        assertThat(book.high()).isEqualTo(101);

        assertThat(book.levels()).containsExactly(new Level(-10, 101));
    }

    @Test
    public void cancel_last_bid() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();

        long bid100 = book.place(10,     100,    555);

        events.clear();
        LOG.info(book.toString());
        book.cancel(bid100, 555);
        assertThat(events).flatExtracting(
                Order::evt,   Order::active,    Order::filled,    Order::price,     Order::cookie,    Order::id).containsExactly(
                CANCEL,         (long)10,       (long)0,            (long)100,      (long)555,      (long)bid100);
        LOG.info(book.toString());
        assertThat(book.hasAsks()).isFalse();
        assertThat(book.hasBids()).isFalse();
        assertThat(book.bid()).isEqualTo(OrderBook.NO_BID);
        assertThat(book.ask()).isEqualTo(OrderBook.NO_ASK);
        assertThat(book.low()).isEqualTo(OrderBook.NO_ASK);
        assertThat(book.high()).isEqualTo(OrderBook.NO_BID);
        assertThat(book.levels()).isEmpty();
        assertThat(book.size()).isEqualTo(0);
    }


    @Test
    public void trade() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();
        long bid100 = book.place(10,     100,    555);

        events.clear();
        long ask101 = book.place(-10,    100,   666);
        assertThat(events).flatExtracting(
                Order::evt,   Order::active,  Order::filled,    Order::price,   Order::cookie,    Order::id). containsExactly(
                FILL,       (long) 0,      (long) 10,      (long) 100,  (long)555,      (long)0,
                FILL,       (long) -10,     (long)-10,      (long) 100,  (long)666,      (long)1);
        assertThat(book.hasAsks()).isFalse();
        assertThat(book.hasBids()).isFalse();
        assertThat(book.size()==0);
    }

    @Test
    public void trade_through() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();
        long bid100 = book.place(10,     100,   555);

        events.clear();
        long ask101 = book.place(-10,    50,    666);
        assertThat(events).flatExtracting(
                Order::evt,   Order::active,  Order::filled,    Order::price,   Order::cookie,    Order::id). containsExactly(
                FILL,       (long) 0,      (long) 10,      (long) 100,  (long)555,      (long)0,
                FILL,       (long)-10,     (long)-10,      (long) 100,  (long)666,      (long)1);
        assertThat(book.hasAsks()).isFalse();
        assertThat(book.hasBids()).isFalse();
        assertThat(book.size()==0);
        assertThat(book.size()).isEqualTo(0);
    }


    //@Test
    public void trade_through_two() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();
        book.place(2,     100,   43);
        book.place(10,    100,   63);
        book.place(2,     102,   62);
        LOG.info(book.toString());
        events.clear();
        book.place(-10,    101,  64);
        LOG.info(book.toString());
        /*assertThat(events).flatExtracting(
                Order::evt,   Order::active,  Order::filled,    Order::price,   Order::cookie,    Order::id). containsExactly(
                FILL,       (long) 0,      (long) 10,      (long) 101,  (long)777,      (long)1,
                FILL,       (long) 10,     (long) 10,      (long) 101,  (long)666,      (long)3
                PLACE,      (long) -10,     (long) 0,      (long) 101,  (long)666,      (long)3
                );*/
        assertThat(book.hasBids()).isTrue();
        assertThat(book.bid()).isEqualTo(100);
        assertThat(book.bidVolume()).isEqualTo(12);

        assertThat(book.hasAsks()).isTrue();
        assertThat(book.ask()).isEqualTo(101);
        assertThat(book.askVolume()).isEqualTo(-10);

        assertThat(book.size()==0);
    }


    @Test
    public void trade_head() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();
        long bid555 = book.place(10,     100,        555);
        long bid777 = book.place(20,     100,        777);

        events.clear();

        long ask666 = book.place(-10,    100,       666);
        assertThat(book.volume(ask666)==0);

        assertThat(events).flatExtracting(
                Order::evt,   Order::active,  Order::filled,    Order::price,   Order::cookie,    Order::id). containsExactly(
                FILL,       (long) 0,      (long) 10,      (long) 100,          (long)555,      (long)bid555,
                FILL,       (long)-10,     (long)-10,      (long) 100,          (long)666,      (long)ask666);
        assertThat(book.hasAsks()).isFalse();
        assertThat(book.bid()).isEqualTo(100);
        assertThat(book.high()).isEqualTo(100);
        assertThat(book.low()).isEqualTo(100);
    }

    @Test
    public void trade_head_partial() {
        ArrayList<OrderMessage> events = new ArrayList<>();
        OffHeapBook book = book(events);

        events.clear();
        long bid555 = book.place(10,     100,        555);
        long bid777 = book.place(20,     100,        777);

        events.clear();

        long ask666 = book.place(-3,    100,       666);
        assertThat(book.volume(ask666)==0);

        // here active means taking liquidity volume
        assertThat(events).flatExtracting(
                Order::evt,   Order::active,  Order::filled,    Order::price,   Order::cookie,    Order::id). containsExactly(
                PARTFILL,       (long) 0,      (long) 3,      (long) 100,          (long)555,      (long)bid555,
                FILL,           (long)-3,      (long)-3,      (long) 100,          (long)666,      (long)ask666);
        assertThat(book.hasAsks()).isFalse();
        assertThat(book.bid()).isEqualTo(100);
        assertThat(book.high()).isEqualTo(100);
        assertThat(book.low()).isEqualTo(100);
    }


    private static final String[] BASE_JVM_ARGS = {
            "-server",
            "-dsa",
            "-da",
            //"-ea:net.openhft...",
            "-XX:+AggressiveOpts",
            "-XX:+UseBiasedLocking",
            "-XX:+UseFastAccessorMethods",
            "-XX:+OptimizeStringConcat",
            "-XX:+HeapDumpOnOutOfMemoryError"
    };

    @Test
    public void bench() throws Exception {

        Options opt = new OptionsBuilder()
                // Specify which benchmarks to run.
                // You can be more specific if you'd like to run only one benchmark per test.
                .include(this.getClass().getName() + ".*")
                // Set the following options as needed
                .mode (Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupTime(TimeValue.seconds(1))
                .warmupIterations(3)
                .measurementTime(TimeValue.seconds(1))
                .measurementIterations(3)
                //.threads(2)
                .forks(1)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                .jvmArgs(BASE_JVM_ARGS)
                .build();

        Collection<RunResult> results = new Runner(opt).run();
    }

    //@Benchmark
    public void emitterBench() {
        AtomicInteger emitted = new AtomicInteger(0);

        OrderEmitter emitter = new OrderEmitter.Builder<OrderEmitter>()
                .onEvent((evt, volume, price, cookie) -> emitted.incrementAndGet())
                .build();

        for(int i = 0; i< NITERS; i++){
            emitter.run();
        }
    }

    public final static int NITERS = 10_000_000;

    @Benchmark
    @Test
    public void bookBench() {
        AtomicInteger events = new AtomicInteger(0);
        //PriorityQueue<OrderMessage> at = new PriorityQueue<>(new Comparator<OrderMessage>() {
        //    @Override
        //    public int compare(OrderMessage o1, OrderMessage o2) {
        //        return (int)(o1.clientTime()-o2.clientTime());
        //    }
        //});

        OffHeapBook book = OffHeapBook.builder()
                //.range(-10000, 10000)
                .onEvent((evt, filled, active, price, id, cookie) -> {
                        events.incrementAndGet();
                        if(!NO_LOGS) {
                            Order ord = new OrderMessage(evt, filled, active, price, id, cookie);
                            LOG.info(String.format("EVENT: %s", ord));
                        }
                })
                .build();

        OrderEmitter emitter = new OrderEmitter.TestOrderEmitter.Builder()
                .onEvent((evt, volume, price, time) -> {
                    if(!NO_LOGS)
                        book.selfcheck();
                    long id = -1;
                    long cookie = time;
                    if(evt==CANCEL) {
                        if(volume>=0 && !book.hasBids() || volume<0 && !book.hasAsks())
                            return;
                        id = volume>0 ? book.head(book.bid()) : book.head(book.ask());
                        cookie = book.cookie(id);

                        if(id<0){
                            assert(false);
                        }
                    }
                    long goodtill = time+10;
                    if(!NO_LOGS) {
                        Order ord = new OrderMessage(evt, 0, volume, price, id, cookie);
                        LOG.info(String.format("ACTION: %s", ord));
                    }
                    book.dispatch(evt, volume, price, id, cookie, goodtill);
                    if(!NO_LOGS) {
                        LOG.info(String.format("STATE: %s", book));
                        book.selfcheck();
                    }
                    if((time%10000) == 0) {
                        book.expire(time);
                    }
                    //if((time%100000) == 0) {
                    //    LOG.info(String.format("***** TIME = %d\n%s",time,book.toString()));
                    //}
                    if(!NO_LOGS)
                        book.selfcheck();
                })
                .build();

        measure(NITERS, emitter);
    }

    void measure(int niters, OrderEmitter emitter) {
        long ns = System.nanoTime();
        for(int i=0; i<niters; i++) {
            emitter.run();
        }
        ns = System.nanoTime()-ns;
        LOG.info(String.format("ITERATIONS: %.2f mio, ELAPSED %.2f s, THROUGHPUT: %.3f mio/s", niters/1e6, ns/1e9, niters/1e6/(ns/1e9)));
    }

    public static void main(String[] args) throws Exception {
        JUnitCore.main("org.freeticks.OrderBookTest");
    }
}