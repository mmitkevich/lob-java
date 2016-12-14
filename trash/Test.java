package org.freeticks;

import com.google.common.collect.Lists;

import java.io.PrintStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class Test<T, R> implements  Runnable {
    public String name;
    public boolean logEmits = true;
    public boolean logEvents = true;
    public boolean logErrors = true;
    public int niters = 1;

    public static PrintStream out = System.err;
    public static int failed = 0;
    public static int passed = 0;

    public Test(String name){
        this.name = name;
    }

    private Function<Test,T> subject = null;
    private Iterator<Expect.Condition<R>> eventsIt = null;
    private Consumer<Test> step = null;
    private AtomicBoolean anything;

    public boolean hasFailures = false;

    public Test of(Function<Test, T> subject) {
        this.subject = subject;
        return this;
    }

    public Test expects(Expect.Condition<R>...events) {
        this.eventsIt = Lists.newArrayList(events).iterator();
        return this;
    }

    public Test with(Consumer<Test> step) {
        this.step = step;
        return this;
    }

    public void run() {
        anything = new AtomicBoolean(false);
        hasFailures = false;
        out.printf("START(%s) niters: %d\n", name, niters);
        long ns = System.nanoTime();
        T subj = subject.apply(this);
        for(int i=0; i<niters; i++) {
            step.accept(subj);
        }
        ns = System.nanoTime() - ns;
        out.printf("PASS(%s) elapsed: %.3f s, throughput: %.2f mio/s, latency: %.2f us, niters: %d\n", name, ns/1e9, niters*1e3/ns, 1e-3*ns/niters, niters);
    }

    public void event(Supplier<R> eventSup) {
        if(logEvents)
            out.printf("EVENT: %s\n", event.get());
        if(eventsIt !=null) {
            R event = eventSup.get();
            if (!eventsIt.hasNext()) {
                fail("unexpected: %s\n", event);
            } else {
                Expect.Condition<R> cond = eventsIt.next();
                if(cond==Expect.anything())
                    eventsIt = null;    // no more tests
                else
                    cond.apply(event, this);
            }
        }
    }

    public <R> void emit(Supplier<R> emit) {
        if(this.logEmits)
            out.printf("EMIT: %s\n", emit.get());
    }

    public void fail(String msg, Object...args){
        if(msg!=null)
            out.printf(String.format("FAIL(%s) ", name) + msg, args);
        hasFailures = true;
    }

    public Test logEmits(boolean value){
        this.logEmits = value;
        return this;
    }

    public Test logEvents(boolean value) {
        this.logEvents = value;
        return this;
    }
    public Test logErrors(boolean value) {
        this.logErrors = value;
        return this;
    }

    public static void logTo(PrintStream ps) {
        out = ps;
    }

    public Test logNothing() {
        logErrors = false;
        logEvents = false;
        logEmits = false;
        return this;
    }

    public Test iters(int niters){
        this.niters = niters;
        return this;
    }

    public static boolean run(Test...tests) {
        for(Test test: tests) {
            if(!test.run())
                failed++;
            else
                passed++;
        }
        out.printf("PASSED: %d, FAILED: %d\n", passed, failed);
        return failed==0;
    }
}
