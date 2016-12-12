package org.freeticks;

import java.util.Random;

import static org.freeticks.OrderBook.Event.PLACE;

public class OrderEmitter {
    public double marketIntensity = 1.0;
    public double limitIntensity = 2.0;
    public double cancelIntensity = 10.0;
    public Distributions distrib;
    public long time;

    public ActionHandler handler;

    public long bestBid;
    public long bestAsk;

    public long bidVolume;
    public long askVolume;
    public long volume;
    public long maxPrice;
    public long minPrice;

    public interface ActionHandler {
        void apply(OrderBook.Event evt, long volume, long price, long time);
    }

    public OrderEmitter(Builder params)
    {
        this.marketIntensity = params.marketIntensity;
        this.limitIntensity = params.limitIntensity;
        this.cancelIntensity = params.cancelIntensity;
        this.distrib = params.distrib;
        this.time = params.time;
        this.bestBid = params.price;
        this.bestAsk = params.price + 1;
        this.bidVolume = params.volume;
        this.askVolume = params.volume;
        this.volume = params.volume;
        this.handler = params.handler;
        this.maxPrice = params.maxPrice;
        this.minPrice = params.minPrice;
    }

    public static class Builder<T extends OrderEmitter>
    {
        public double marketIntensity = 1.0;
        public double limitIntensity = 2.0;
        public double cancelIntensity = 10.0;
        public Distributions distrib;
        public Random random;
        public long time;

        public long price = 100;
        public long volume = 1000;

        public long maxPrice = 100000;
        public long minPrice = -100000;

        public ActionHandler handler;

        public Builder price(long price) {
            this.price = price;
            return this;
        }

        public Builder time(long time) {
            this.time = time;
            return this;
        }

        public Builder onEvent(ActionHandler handler) {
            this.handler = handler;
            return this;
        }

        public Builder market(double marketIntensity) {
            this.marketIntensity = marketIntensity;
            return this;
        }

        public Builder limit(double limitIntensity) {
            this.limitIntensity = limitIntensity;
            return this;
        }

        public Builder cancel(double cancelIntensity) {
            this.cancelIntensity = cancelIntensity;
            return this;
        }

        public Builder random(Random random) {
            this.random = random;
            return this;
        }

        public Builder distributions(Distributions distrib) {
            this.distrib = distrib;
            this.random = distrib.random;
            return this;
        }

        public OrderEmitter build() {
            if(random==null)
                 random = new Random(System.nanoTime());
            if(distrib==null)
                distrib = new Distributions(random);
            return new OrderEmitter(this);
        }
    }

    public void deplete(int delta)
    {
        bestBid+=delta;
        bestAsk+=delta;
        askVolume = volume;
        bidVolume = volume;
        if(handler!=null)
        {
            time++;
            handler.apply(PLACE, bidVolume, bestBid, time);
            time++;
            handler.apply(PLACE, -askVolume, bestAsk, time);
        }
    }

    public void run() {
        int k;

        if(time==0)
            deplete(0);

        k = distrib.poisson(marketIntensity);
        if(k>0)
            emit(OrderBook.Event.FILL, k); // market sell order

        k = distrib.poisson(cancelIntensity);
        if(k>0)
            emit(OrderBook.Event.CANCEL, k); // limit buy order cancel

        k = distrib.poisson(limitIntensity);
        if(k>0)
            emit(PLACE, k); // new limit buy order

        k = distrib.poisson(marketIntensity);
        if(k>0)
            emit(OrderBook.Event.FILL, -k);

        k = distrib.poisson(cancelIntensity);
        if(k>0)
            emit(OrderBook.Event.CANCEL, -k);

        k = distrib.poisson(limitIntensity);
        if(k>0)
            emit(PLACE, -k);
    }
    public void emit(OrderBook.Event evt, long vol) {
        int size = 0;
        switch(evt) {
            case FILL: evt = PLACE; size = -1; break;   // FILL means PLACE aggressively
            case CANCEL: size = -1; break;
            case PLACE: size = 1; break;
            default: throw new IllegalArgumentException();
        }

        time++;
        if(vol>0)
        {
            if(handler!=null)
                handler.apply(evt, vol*size, bestBid, time);
            bidVolume += Math.abs(vol)*size;
            if(bidVolume<=0){
                deplete(1);
            }
        }
        else {
            if(handler!=null)
                handler.apply(evt, vol*size, bestAsk, time);
            askVolume += Math.abs(vol)*size;
            if (askVolume <= 0) {
                deplete(-1);
            }
        }

    }

}

