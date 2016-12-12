package org.freeticks;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Iterator;

public class OrderBook
{
    protected static final Logger LOG = LoggerFactory.getLogger(OrderBook.class);

    public static final int MAX_PRICE_LEVELS = 1024;
    public static final int ARENA_SIZE = 65536;
    public static final int INDEX_HALF_SIZE = 4096;

    public enum Event {
        PLACE,
        FILL,
        PARTFILL,
        CANCEL,
        REJECT,
        REJECT_CANCEL
    }

    @FunctionalInterface
    public interface EventHandler {
        void apply (Event evt, long filled, long active, long price, long id, long cookie);
    }

    @FunctionalInterface
    public interface ErrorHandler {
        void apply (Event evt, long id, long cookie);
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

        public OrderBook build() {
            return new OrderBook(capacity, minprice, maxprice, handler, error);
        }
    }

    class Orders {
        final int VOLUME     = 0;
        final int PRICE      = 8;
        final int NEXT       = 16;
        final int COOKIE     = 20;
        final int GOODTILL   = 28;
        final int LENGTH     = 36;

        ByteBuffer bytes;
        int head;
        int tail;
        int capacity;

        Orders(int capacity) {
            this.capacity = capacity;
            bytes = ByteBuffer.allocateDirect(LENGTH * capacity);
            head = tail = 0;
        }

        public int capacity() {
            return capacity;
        }

        public int nextFreeSlot() {
            assert (isFree(head));
            int cap = capacity;
            int h = head;
            for (int i = 1; i < cap; i++) {
                int j = (h + i) % cap;
                if (isFree(j)) {
                    return j;
                }
            }
            return -1;
        }

        public void setHead(int head) {
            this.head = head;
        }

        public boolean isFree(int i) {
            if(i<0 || i>capacity())
                throw new IndexOutOfBoundsException(String.format("isFree %d",i));
            return volume(i) == 0;
        }

        public void free(int i) {
            setVolume(i, 0);
            setPrice(i, 0);
            setNext(i, -1);
            setCookie(i, 0);
            setGoodTill(i, 0);
        }

        int addr(int i){
            return i*LENGTH;
        }

        public long price(int i) {
            return bytes.getLong(addr(i) + PRICE);
        }
        void setPrice(int i, long value) { bytes.putLong(addr(i) + PRICE, value);}

        public long volume(int i) {
            long value = bytes.getLong(addr(i) + VOLUME);
            return value;
        }

        void setVolume(int i, long value) {
            bytes.putLong(addr(i) + VOLUME, value);
        }

        public int next(int i) {
            return bytes.getInt(addr(i) + NEXT) - 1;
        }
        void setNext(int i, int value) {
            bytes.putInt(addr(i) + NEXT, value + 1);
        }

        public long goodTill(int i) {
            return bytes.getLong(addr(i) + GOODTILL);
        }
        void setGoodTill(int i, long value) {
            bytes.putLong(addr(i) + GOODTILL, value);
        }

        public long cookie(int i) {
            return bytes.getLong(addr(i) + COOKIE);
        }
        void setCookie(int i, long value) {
            bytes.putLong(addr(i) + COOKIE, value);
        }
    }

    class Levels implements Iterable<Level>{
        final int VOLUME = 0;
        final int HEAD = 8;
        final int TAIL = 12;
        //public final int NEXT = 16;
        //public final int PREV = 24;

        final int LENGTH = 16;

        ByteBuffer bytes;
        long min;
        long max;


        Orders arena;

        Levels(Orders arena, long min, long max) {
            this.arena = arena;
            int cap = (int)(max-min+1)*LENGTH;
            bytes = ByteBuffer.allocateDirect(cap);
            this.min = min;
            this.max = max;
        }

        public boolean isEmpty(long i) {
            if(i<min || i>max)
                return true;
            return head(i) == -1;
        }

        int addr(long price) {
            return (int)((price-min) * LENGTH);
        }

        public long volume(long price) {
            return bytes.getLong(addr(price) + VOLUME);
        }
        void setVolume(long price, long value) {
            bytes.putLong(addr(price) + VOLUME, value);
        }

        public int head(long price) { return bytes.getInt(addr(price) + HEAD) -1; }
        void setHead(long i, int value) {
            bytes.putInt(addr(i) + HEAD, value + 1);
        }

        public int tail(long i) {
            return bytes.getInt(addr(i) + TAIL) - 1;
        }
        void setTail(long i, int value) {
            bytes.putInt(addr(i) + TAIL, value + 1) ;
        }

       // public long nextFreeSlot(long price) { return bytes.getLong(addr(price)+ NEXT); }
       // void setNext(long price, long value) { this.bytes.putLong(addr(price) + NEXT, value);}

       // public long prev(long price) { return bytes.getLong(addr(price)+ PREV); }
       // void setPrev(long price, long value) { this.bytes.putLong(addr(price) + PREV, value);}

        void free(long price) {
            int i;
            while((i = head(price))!=-1) {
                arena.free(i);
            }

            setVolume(price, 0);
            //setNext(price, 0);
            //setPrev(price, 0);
            setHead(price, -1);
            setTail(price, -1);
        }

        void keepBest(int n){
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


        @Override
        public Iterator<Level> iterator() {
            return new Iterator<Level>() {
                public long price = lowPrice;
                @Override
                public boolean hasNext() {
                    return price <= highPrice;
                }

                @Override
                public Level next() {
                    Level lvl = new Level(volume(price), price);
                    price++;
                    return lvl;
                }
            };
        }

        public Iterable<Order> orders (long price) {
            return new Iterable<Order>() {
                @Override
                public Iterator<Order> iterator() {
                    return new Iterator<Order>() {
                        int id = head(price);
                        @Override
                        public boolean hasNext() {
                            return id!=-1;
                        }

                        @Override
                        public Order next() {

                            OrderMessage order = new  OrderMessage(Event.PLACE, 0, arena.volume(id), arena.price(id), id, arena.cookie(id));
                            id = arena.next(id);
                            return order;
                        }
                    };
                }
            };
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("**** #:%d, l:%d, h:%d, bid:%d ask:%d\n", size(), low(), high(), bid(), ask()));
            for(Level lvl: this) {
                String side = "   ";
                if(lvl.price()<=bestBid)
                    side = "BID";
                if(lvl.price()>=bestAsk)
                    side = "ASK";
                sb.append(String.format("%s %d", side, lvl.price(), lvl.volume()));
                sb.append('[');
                for(Order ord: orders(lvl.price())) {
                    sb.append(String.format("{%d %d#%d} ",ord.active(), ord.cookie(), ord.id()));
                }
                sb.append("]\n");
            }
            return sb.toString();
        }
    }

    private Levels index;

    private long bestBid;
    private long bestAsk;

    private long highPrice;
    private long lowPrice;

    private EventHandler handler;
    private ErrorHandler error;
    private Orders orders;
    private int size;

    public final static long NO_BID = -Long.MAX_VALUE;
    public final static long NO_ASK = Long.MAX_VALUE;

    public OrderBook(int capacity, long minprice, long maxprice, EventHandler handler, ErrorHandler error) {
        orders = new Orders(capacity);
        index = new Levels(orders, minprice, maxprice);
        this.handler = handler;
        this.error = error;
        lowPrice = bestAsk = NO_ASK;
        highPrice = bestBid = NO_BID;
    }

    public Levels levels() {
        return this.index;
    }

    public Orders orders() {
        return this.orders;
    }

    public long bid() { return bestBid; }
    public long bidVolume() {return levels().volume(bestBid);}

    public long ask() {
        return bestAsk;
    }
    public long askVolume() {return levels().volume(bestAsk);}


    public long low() { return lowPrice; }
    public long high() { return highPrice; }

    public boolean hasBid() { return bestBid!=NO_BID; }
    public boolean hasAsk() { return bestAsk!=NO_ASK; }

    public int size() {
        return size;
    }

    public final static long IOC = 0;
    public final static long GTC = -1;

    public int placeLimit(long volume, long price, long cookie, long goodTill) {
        return place(volume, price, cookie, goodTill);
    }

    public int immediateOrCancel(long volume, long price, long cookie) {
        return place(volume, price, cookie, IOC);
    }

    private int place(long volume, long price, long cookie, long goodTill) {

        if(size>4*orders.capacity()/5) {
            LOG.info("capacity exhausted");
        }

        int nextId = orders.nextFreeSlot();

        if(nextId < 0)
        {
            if(error!=null)
                error.apply(Event.REJECT, -1, cookie);
            return -1;
        }

        int id = orders.head;

        int dir = volume>0?1:-1;

        volume = match(volume, price, id, cookie);

        if(goodTill == IOC || volume == 0)
            return id;      // ensures volume(id)==0

        // new order can update high/low prices
        lowPrice = Math.min(lowPrice, price);
        highPrice = Math.max(highPrice, price);

        orders.setPrice(id, price);
        orders.setVolume(id, volume);
        orders.setCookie(id, cookie);
        orders.setHead(nextId);
        orders.setGoodTill(id, goodTill);

        if(index.isEmpty(price)) {
            index.setHead(price, id);
        }else {
            orders.setNext(index.tail(price), id);
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
            handler.apply(Event.PLACE, 0, volume, price, id, cookie);
        return id;
    }

    public void selfcheck() {
        if(hasBid()) {
            assert (bestBid!=NO_BID);
            if(!(index.head(bestBid)!=-1)){
                LOG.error("weird");
            }
        }
        if(hasAsk()) {
            assert (bestAsk!=NO_ASK);
            assert (index.head(bestAsk)!=-1);
        }
    }

    /*
    void linkLevels(long price){
        long p;
        for(p = price-1; p>=lowPrice; p--) {
            index.setNext(p, price);
            if(!index.isEmpty(p))
            {
                index.setPrev(price, p);
                break;
            }
        }

        for(p = price+1; p<=highPrice; p++) {
            index.setPrev(p, price);
            if(!index.isEmpty(p)) {
                index.setNext(price, p);
                break;
            }
        }
    }*/


    private long matchLevel(long price, int headId, long activeVolume, int activeId, long activeCookie){
        while (headId != -1) {
            long passiveVolume = orders.volume(headId);
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
            int nextId;
            passiveVolume-=filled;
            long passiveCookie = orders.cookie(headId);
            if (passiveVolume==0) {
                nextId = pop(headId);
            }else {
                nextId = headId;
                orders.setVolume(headId, passiveVolume*passiveDir);
            }
            index.setVolume(price, index.volume(price) - filled*passiveDir);
            activeVolume -= filled;

            if (handler != null) // FILL
            {
                handler.apply(passiveVolume==0 ? Event.FILL:Event.PARTFILL, filled*passiveDir, 0, price, headId, passiveCookie);   // active=0 means that volume was passive
                handler.apply(activeVolume==0 ? Event.FILL:Event.PARTFILL, filled*activeDir, filled*activeDir, price, activeId, activeCookie);
            }
            headId = nextId;
            if(activeVolume == 0)
                break;
        }
        return activeVolume;
    }

    public long match(long activeVolume, long price, int activeId, long activeCookie) {
        long dir = activeVolume > 0 ? 1:-1;
        //long best = dir > 0 ? bestBid:bestAsk;
        //long last = dir > 0 ? highPrice:lowPrice;

        // this will loop hard to find nextFreeSlot not empty price with some orders to set best bid/best ask
        if(dir>0) {
            while(bestAsk<=highPrice) {
                int headId = index.head(bestAsk);
                if (bestAsk<=price && activeVolume!=0 && headId != -1) {
                    activeVolume = matchLevel(bestAsk, headId, activeVolume, activeId, activeCookie);
                }
                if(!index.isEmpty(bestAsk))
                    break;  // some volume left
                bestAsk++;
            }
            if(bestAsk>highPrice) {
                bestAsk = NO_ASK;
                highPrice = bestBid;
                if (bestBid == NO_BID) {
                    lowPrice = NO_ASK;
                }
            }
        }else{
            while(bestBid>=lowPrice) {
                int headId = index.head(bestBid);
                if (bestBid>=price && activeVolume!=0 && headId != -1) {
                    activeVolume = matchLevel(bestBid, headId, activeVolume, activeId, activeCookie);
                }
                if(!index.isEmpty(bestBid))
                    break;  // some volume left
                bestBid--;
            }
            if(bestBid<lowPrice) {
                bestBid = NO_BID;
                lowPrice = bestAsk;
                if (bestAsk == NO_ASK) {
                    highPrice = NO_BID;
                }
            }
        }
        return activeVolume;
    }

    private int pop(int i) {
        long price = orders.price(i);
        int next = orders.next(i);
        index.setHead(price, next);
        if(next==-1)
            index.setTail(price, -1);
        index.setVolume(price, index.volume(price) - orders.volume(i));
        orders.free(i);
        size--;
        return next;
    }

    public void expire(long time) {
        for(long price=lowPrice; price<=highPrice; price++) {
            int id = index.head(price);
            while(id!=-1) {
                long gt = orders.goodTill(id);
                if (gt > 0 && gt < time) {
                    this.cancel(id, orders.cookie(id));
                    id = index.head(id);
                } else {
                    id = orders.next(id);
                }
            }
        }
    }


    public int dispatch(OrderBook.Event evt, long volume, long price, int id, long cookie, long goodTill)
    {
        switch(evt){
            case PLACE:
                return placeLimit(volume, price, cookie, goodTill);

            case CANCEL:
                cancel(id, cookie);
                return id;
            default:
                throw new java.util.NoSuchElementException("evt");
        }
    }

    public long cancel(int id, long cookie) {
        if(orders.isFree(id)) {
            if(error!=null)
                error.apply(Event.REJECT_CANCEL, id, cookie);
            return 0;
        }
        long volume = orders.volume(id);
        long price = orders.price(id);
        cookie = orders.cookie(id);
        pop(id);
        long left = index.volume(price);
        if(index.isEmpty(price)) {
            // fix low/high prices
            long p = price;
            if(lowPrice==highPrice){
                lowPrice = bestAsk = NO_ASK;
                highPrice = bestBid = NO_BID;
            }else if(p==highPrice)
            {
                if(p == bestAsk)
                    bestAsk = NO_ASK;
                while (p > lowPrice && index.isEmpty(p))
                    p--;        // potentially this price is highprice now
                highPrice = p;
            }else if(p==lowPrice) {
                if(p == bestBid)
                    bestBid = NO_BID;
                while (p < highPrice && index.isEmpty(p))
                    p++;        // potentially this price is lowPrice now
                lowPrice = p;
            }
            // fix best bid/best ask
            p = price;
            if(p == bestBid) {
                while(p >= lowPrice && index.isEmpty(p))
                    p--;
                bestBid = p;
            }else if(p == bestAsk) {
                while(p <= highPrice && index.isEmpty(p))
                    p++;
                bestAsk = p;
            }
        }
        if(handler != null)
            handler.apply(Event.CANCEL, 0, volume, price, id, cookie);
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
            if(!index.isEmpty(price)) {
                sb.append(price);
                sb.append("->");
                int i = index.head(price);
                while(i!=-1) {
                    sb.append(orders.volume(i));
                    i = orders.next(i);
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
