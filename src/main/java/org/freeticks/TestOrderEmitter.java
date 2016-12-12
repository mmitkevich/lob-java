package org.freeticks;

import static org.freeticks.OrderBook.Event.PLACE;

/**
 * Created by mike on 12.12.16.
 */
public class TestOrderEmitter extends OrderEmitter {

    public TestOrderEmitter(OrderEmitter.Builder params){
        super(params);
    }

    public static class Builder extends OrderEmitter.Builder<TestOrderEmitter> {
        public TestOrderEmitter build(){
            return new TestOrderEmitter(this);
        }
    }

    public void run() {
        int k;

        if (handler != null) {
            handler.apply(OrderBook.Event.PLACE, 1, bestBid, time);
            time++;
            handler.apply(OrderBook.Event.CANCEL, 1, bestBid, time);
            time++;
            handler.apply(OrderBook.Event.PLACE, 1, bestBid, time);
            time++;
            handler.apply(OrderBook.Event.PLACE, -1, bestBid, time);
            time++;
        }
    }
}
