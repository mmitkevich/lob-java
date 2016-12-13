package eu.exante.freeticks;

import net.openhft.chronicle.core.annotation.ForceInline;
import org.freeticks.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.LongStream;
import java.util.stream.StreamSupport;

import static java.util.stream.LongStream.concat;
import static java.util.stream.StreamSupport.intStream;
import static java.util.stream.StreamSupport.longStream;
import static java.util.stream.StreamSupport.stream;
import static org.freeticks.OrderType.IOC;

public class OffHeapBook extends UnsafeBuffer implements OrderBook
{
    protected static final Logger LOG = LoggerFactory.getLogger(OffHeapBook.class);

    final static boolean BOUNDS = true;

    public static final int MAX_PRICE_LEVELS = 1024;
    public static final int ARENA_SIZE = 65536;
    public static final int INDEX_HALF_SIZE = 4096;

    final static int VOLUME     = 0;
    final static int PRICE      = 8;
    final static int NEXT       = 16;
    final static int COOKIE     = 24;
    final static int GOODTILL   = 32;
    final static int ELEMENT_SIZE = 40;

    long head;
    long tail;
    long capacity;

    long nextFreeSlot() {
        assert (isFree(head));
        long cap = capacity;
        long h = head;
        for (int i = 1; i < cap; i++) {
            long j = (h + i) % cap;
            if (isFree(j)) {
                return j;
            }
        }
        return -1;
    }

    void setHead(long head) {
        this.head = head;
    }

    boolean isFree(long id) {
        return volume(id) == 0;
    }

    public void free(long i) {
        setVolume(i, 0);
        setPrice(i, 0);
        setNext(i, -1);
        setCookie(i, 0);
        setGoodTill(i, 0);
    }

    long field(long i, long offset){
        return headAddress + i* ELEMENT_SIZE + offset;
    }

    public long price(long i) {
        return getLong(field(i, PRICE));
    }
    void setPrice(long i, long value) { putLong(field(i, PRICE), value);}

    public long volume(long i) { return getLong(field(i, VOLUME)); }
    void setVolume(long i, long value) {
        putLong(field(i, VOLUME), value);
    }

    public long next(long id) { return getLong(field(id, NEXT)) - 1; }
    void setNext(long id, long value) {
        putLong(field(id, NEXT), value + 1);
    }

    public long goodTill(long id) {
        return getLong(field(id, GOODTILL));
    }
    void setGoodTill(long id, long value) {
        putLong(field(id, GOODTILL), value);
    }

    public long cookie(long id) {
        return getLong(field(id, COOKIE));
    }
    void setCookie(long id, long value) {
        putLong(field(id, COOKIE), value);
    }


    @FunctionalInterface
    public interface EventHandler {
        void apply (int evt, long filled, long active, long price, long id, long cookie);
    }

    @FunctionalInterface
    public interface ErrorHandler {
        void apply (int evt, long id, long cookie);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder
    {
        private int capacity = 65536;
        private long minprice = -100000;
        private long maxprice = 100000;
        private EventHandler handler;
        private ErrorHandler error;

        public Builder onEvent(EventHandler handler) {
            this.handler = handler;
            return this;
        }

        public Builder onError(ErrorHandler error) {
            this.error = error;
            return this;
        }

        public Builder range(long minprice, long maxprice) {
            this.minprice = minprice;
            this.maxprice = maxprice;
            return this;
        }

        public OffHeapBook build() {
            return new OffHeapBook(capacity, minprice, maxprice, handler, error);
        }
    }

    class OffHeapLevels extends UnsafeBuffer implements Levels {
        final static int VOLUME = 0;
        final static int HEAD = 8;
        final static int TAIL = 16;
        final static int ELEMENT_SIZE = 24;

        long min;
        long max;

        OffHeapLevels(long min, long max) {
            super(max-min+1, ELEMENT_SIZE);
            this.min = min;
            this.max = max;
        }

        public boolean contains(long price) {
            if(price<min || price>max)
                return false;
            return head(price) == -1;
        }

        @ForceInline
        long field(long price, long offset) {
            return headAddress + (price-min) * ELEMENT_SIZE + offset;
        }

        @ForceInline
        public long volume(long price) { return getLong(field(price, VOLUME)); }
        @ForceInline
        void setVolume(long price, long value) {
            putLong(field(price, VOLUME), value);
        }

        @ForceInline
        public long head(long price) { return getLong(field(price, HEAD)) -1; }
        @ForceInline
        void setHead(long i, long value) { putLong(field(i, HEAD), value + 1); }

        @ForceInline
        public long tail(long i) {
            return getLong(field(i, TAIL)) - 1;
        }
        @ForceInline
        void setTail(long i, long value) {
            putLong(field(i, TAIL), value + 1) ;
        }

        void free(long price) {
            long id;
            while((id = head(price))!=-1) {
                OffHeapBook.this.free(id);
            }

            setVolume(price, 0);
            //setNext(price, 0);
            //setPrev(price, 0);
            setHead(price, -1);
            setTail(price, -1);
        }

        void keep(int n){
            long price;
            if(bestAsk!=NO_ASK) {
                for (price = bestAsk + n + 1; price <= highPrice; price++) {
                    free(price);
                }
                highPrice = Math.min(highPrice, bestAsk + n);
            }
            if(bestBid!=NO_BID) {
                for (price = bestBid - n - 1; price >= lowPrice; price--) {
                    free(price);
                }
                lowPrice = Math.max(lowPrice, bestBid - n);
            }
        }

        public Iterator<Level> iterator() {
            return concat(bids(), asks()).mapToObj(price -> get(price)).iterator();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("**** #:%d, l:%d, h:%d, bid:%d ask:%d\n", size(), low(), high(), bid(), ask()));
            concat(bids(), asks())
            .forEach(price -> {
                String side = "   ";
                if(price<=bestBid)
                    side = "BID";
                if(price>=bestAsk)
                    side = "ASK";
                sb.append(String.format("%s %d", side, price, volume(price)));
                sb.append('[');
                orders(price).forEach(id -> {
                    Order ord = OffHeapBook.this.get(id);
                    sb.append(String.format("{%d %d#%d} ",ord.active(), ord.cookie(), ord.id()));
                });
                sb.append("]\n");
            });
            return sb.toString();
        }
    }


    private OffHeapLevels index;

    private long bestBid;
    private long bestAsk;

    private long highPrice;
    private long lowPrice;

    private EventHandler handler;
    private ErrorHandler error;
    private int size;

    public OffHeapBook(long capacity, long minprice, long maxprice, EventHandler handler, ErrorHandler error) {
        super(capacity, ELEMENT_SIZE);
        this.capacity = capacity;
        head = tail = 0;
        index = new OffHeapLevels(minprice, maxprice);
        this.handler = handler;
        this.error = error;
        lowPrice = bestAsk = NO_ASK;
        highPrice = bestBid = NO_BID;
    }

    public Levels levels() {
        return this.index;
    }

    @Override
    public LongStream bids() {
        return bestBid==NO_BID ? LongStream.empty() : LongStream.range(0, bestBid-lowPrice+1).map(i->bestBid-i)
                .filter(price->!index.contains(price));
    }

    @Override
    public LongStream asks() {
        return bestAsk==NO_ASK ? LongStream.empty() : LongStream.range(bestAsk, highPrice+1)
                .filter(price->!index.contains(price));
    }

    @Override
    public LongStream orders(long price) {
        return StreamSupport.longStream(
                Spliterators.spliteratorUnknownSize(
                        new PrimitiveIterator.OfLong() {
                            long id = head(price);
                            @Override
                            public boolean hasNext() {
                                return id!=-1;
                            }

                            @Override
                            public long nextLong() {
                                long result = id;
                                id = OffHeapBook.this.next(id);
                                return result;
                            }
                        },
                        Spliterator.ORDERED),
                false);
    }


    public Order get(long id) {
        return new OrderMessage(OrderEvent.PLACE, 0, volume(id), price(id), id, cookie(id));
    }

    public long bid() { return bestBid; }
    public long bidVolume() {return levels().volume(bestBid);}

    public long ask() {
        return bestAsk;
    }
    public long askVolume() {return levels().volume(bestAsk);}

    public long head(long price) {
        return index.head(price);
    }

    public long low() { return lowPrice; }
    public long high() { return highPrice; }

    public boolean hasBids() { return bestBid!=NO_BID; }
    public boolean hasAsks() { return bestAsk!=NO_ASK; }

    public int size() {
        return size;
    }

    public long place(long volume, long price, long cookie, long goodTill) {

        //if(size>4*at.capacity()/5) {
        //    LOG.info("capacity exhausted");
        //}

        long nextId = nextFreeSlot();

        if(nextId < 0)
        {
            if(error!=null)
                error.apply(OrderEvent.REJECT, -1, cookie);
            return -1;
        }

        long id = head;

        int dir = volume>0?1:-1;

        volume = match(volume, price, id, cookie);

        if(goodTill == IOC || volume == 0)
            return id;      // ensures volume(id)==0

        // new order can update high/low prices
        lowPrice = Math.min(lowPrice, price);
        highPrice = Math.max(highPrice, price);

        setPrice(id, price);
        setVolume(id, volume);
        setCookie(id, cookie);
        setHead(nextId);
        setGoodTill(id, goodTill);

        if(index.contains(price)) {
            index.setHead(price, id);
        }else {
            setNext(index.tail(price), id);
        }
        index.setTail(price, id);
        index.setVolume(price, index.volume(price) + volume);

        //linkLevels(price);

        size++;
        if(dir>0)
            bestBid = Math.max(bestBid, price);
        else
            bestAsk = Math.min(bestAsk, price);

        if(handler!=null) // PLACED
            handler.apply(OrderEvent.PLACE, 0, volume, price, id, cookie);
        return id;
    }

    public void selfcheck() {
        if(hasBids()) {
            assert (bestBid!=NO_BID);
            if(!(index.head(bestBid)!=-1)){
                LOG.error("weird");
            }
        }
        if(hasAsks()) {
            assert (bestAsk!=NO_ASK);
            assert (index.head(bestAsk)!=-1);
        }
    }

    /*
    void linkLevels(long price){
        long p;
        for(p = price-1; p>=low; p--) {
            index.setNext(p, price);
            if(!index.contains(p))
            {
                index.setPrev(price, p);
                break;
            }
        }

        for(p = price+1; p<=high; p++) {
            index.setPrev(p, price);
            if(!index.contains(p)) {
                index.setNext(price, p);
                break;
            }
        }
    }*/


    private long matchLevel(long price, long headId, long activeVolume, long activeId, long activeCookie){
        while (headId != -1) {
            // TODO: expire here?
            long passiveVolume = volume(headId);
            int activeDir = 1;
            if(activeVolume < 0) {
                activeVolume = -activeVolume;
                activeDir = -1;
            }
            int passiveDir = 1;
            if(passiveVolume < 0) {
                passiveVolume = -passiveVolume;
                passiveDir = -1;
            }
            long filled = Math.min(activeVolume, passiveVolume);
            long nextId;
            passiveVolume-=filled;
            long passiveCookie = cookie(headId);
            if (passiveVolume==0) {
                nextId = pop(headId);
            }else {
                nextId = headId;
                setVolume(headId, passiveVolume*passiveDir);
            }
            index.setVolume(price, index.volume(price) - filled*passiveDir);
            activeVolume -= filled;

            if (handler != null) // FILL
            {
                handler.apply(passiveVolume==0 ? OrderEvent.FILL:OrderEvent.PARTFILL, filled*passiveDir, 0, price, headId, passiveCookie);   // active=0 means that volume was passive
                handler.apply(activeVolume==0 ? OrderEvent.FILL:OrderEvent.PARTFILL, filled*activeDir, filled*activeDir, price, activeId, activeCookie);
            }
            headId = nextId;
            if(activeVolume == 0)
                break;
        }
        return activeVolume;
    }

    public long match(long activeVolume, long price, long activeId, long activeCookie) {
        long dir = activeVolume > 0 ? 1:-1;

        // this will loop hard to find nextFreeSlot not empty price with some at to set best bid/best ask
        if(dir>0) {
            for(;;) {
                if(bestAsk>highPrice){
                    bestAsk = NO_ASK;
                    highPrice = bestBid;
                    if (bestBid == NO_BID) {
                        lowPrice = NO_ASK;
                    }
                    break;
                }
                if(activeVolume!=0) {
                    long headId = index.head(bestAsk);
                    if (headId != -1 && bestAsk <= price) {
                        activeVolume = matchLevel(bestAsk, headId, activeVolume, activeId, activeCookie);
                    }
                }
                if(!index.contains(bestAsk))
                    break;  // some volume left
                bestAsk++;
            }
        }else{
            for(;;) {
                if(bestBid<lowPrice) {
                    bestBid = NO_BID;
                    lowPrice = bestAsk;
                    if (bestAsk == NO_ASK) {
                        highPrice = NO_BID;
                    }
                    break;
                }
                if(activeVolume!=0) {
                    long headId = index.head(bestBid);
                    if (headId != -1 && bestBid >= price) {
                        activeVolume = matchLevel(bestBid, headId, activeVolume, activeId, activeCookie);
                    }
                }
                if(!index.contains(bestBid))
                    break;  // some volume left
                bestBid--;
            }
        }
        return activeVolume;
    }

    private long pop(long id) {
        long price = price(id);
        long next = next(id);
        index.setHead(price, next);
        if(next==-1)
            index.setTail(price, -1);
        index.setVolume(price, index.volume(price) - volume(id));
        free(id);
        size--;
        return next;
    }

    public void expire(long time) {
        for(long price=lowPrice; price<=highPrice; price++) {
            long id = index.head(price);
            while(id!=-1) {
                long gt = goodTill(id);
                if (gt > 0 && gt < time) {
                    this.cancel(id, cookie(id));
                    id = index.head(id);
                } else {
                    id = next(id);
                }
            }
        }
    }


    public long dispatch(int evt, long volume, long price, long id, long cookie, long goodTill)
    {
        switch(evt){
            case OrderEvent.PLACE:
                return place(volume, price, cookie, goodTill);

            case OrderEvent.CANCEL:
                cancel(id, cookie);
                return id;
            default:
                throw new java.util.NoSuchElementException("evt");
        }
    }

    public long cancel(long id, long cookie) {
        if(isFree(id)) {
            if(error!=null)
                error.apply(OrderEvent.REJECT_CANCEL, id, cookie);
            return 0;
        }
        long volume = volume(id);
        long price = price(id);
        cookie = cookie(id);
        pop(id);
        long left = index.volume(price);
        if(index.contains(price)) {
            // fix low/high prices
            long p = price;
            if(lowPrice==highPrice){
                lowPrice = bestAsk = NO_ASK;
                highPrice = bestBid = NO_BID;
            }else if(p==highPrice)
            {
                if(p == bestAsk)
                    bestAsk = NO_ASK;
                while (p > lowPrice && index.contains(p))
                    p--;        // potentially this price is highprice now
                highPrice = p;
            }else if(p==lowPrice) {
                if(p == bestBid)
                    bestBid = NO_BID;
                while (p < highPrice && index.contains(p))
                    p++;        // potentially this price is low now
                lowPrice = p;
            }
            // fix best bid/best ask
            p = price;
            if(p == bestBid) {
                while(p >= lowPrice && index.contains(p))
                    p--;
                bestBid = p;
            }else if(p == bestAsk) {
                while(p <= highPrice && index.contains(p))
                    p++;
                bestAsk = p;
            }
        }
        if(handler != null)
            handler.apply(OrderEvent.CANCEL, 0, volume, price, id, cookie);
        return left;
    }

    private String format(long start, long end) {
        if(start<index.min)
            start=index.min;
        if(start>index.max)
            start=index.max;
        if(end>index.max)
            end=index.max;
        if(end<index.min)
            end=index.min;
        StringBuilder sb = new StringBuilder();
        long price;
        long dir = start<=end ? 1: -1;
        for(price = start; price*dir <= end*dir; price += dir)
            if(!index.contains(price)) {
                sb.append(price);
                sb.append("->");
                long id = index.head(price);
                while(id!=-1) {
                    sb.append(volume(id));
                    id = next(id);
                    sb.append(' ');
                }
            }
        return sb.toString();
    }

    @Override
    public String toString() {
        return index.toString();
    }
}
