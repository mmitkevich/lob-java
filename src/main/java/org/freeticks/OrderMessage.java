package org.freeticks;

import java.util.function.Consumer;


public class OrderMessage implements Order {
    private OrderBook.Event evt;
    private long filled;
    private long active;
    private long price;
    private long id;
    private long cookie;
    private long serverTime;
    private long clientTime;

    public OrderMessage(OrderBook.Event evt) {
        this.evt = evt;
    }

    public OrderMessage(OrderBook.Event evt, long filled, long active, long price, long id, long cookie) {
        this.evt = evt;
        this.filled = filled;
        this.active = active;
        this.price = price;
        this.id = id;
        this.cookie = cookie;
    }

    public OrderMessage mutate(Consumer<OrderMessage> mutator) {
        mutator.accept(this);
        return this;
    }


    @Override
    public String toString() {
        return String.format("%s{active:%d, price:%d, filled:%d, id:%d, cookie:%d}", evt, active, price, filled, id, cookie);
    }

    @Override
    public OrderBook.Event evt() {
        return evt;
    }

    @Override
    public long id() {
        return id;
    }

    @Override
    public long price() {
        return price;
    }

    @Override
    public long active() {
        return active;
    }

    @Override
    public long filled() {
        return filled;
    }

    @Override
    public long serverTime() {
        return serverTime;
    }

    @Override
    public long clientTime() {
        return clientTime;
    }

    @Override
    public long cookie() {
        return cookie;
    }
}
