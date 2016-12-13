package org.freeticks;

public final class OrderMessage extends Message implements Order {
    long filled;
    long active;
    long price;

    public OrderMessage() { }

    public OrderMessage(int evt) {
        this.evt = evt;
    }

    public OrderMessage(int evt, long filled, long active, long price, long id, long cookie) {
        super(evt, id);
        this.evt = evt;
        this.filled = filled;
        this.active = active;
        this.price = price;
        this.cookie = cookie;
    }

    @Override
    public String toString() {
        return String.format("%s{active:%d, price:%d, filled:%d, id:%d, cookie:%d}", evt, active, price, filled, id, cookie);
    }


    @Override
    public long price() {
        return price;
    }

    public void setPrice(long value) {
        this.price = value;
    }

    @Override
    public long active() {
        return active;
    }

    public void setActive(long value) {
        this.active = value;
    }

    @Override
    public long filled() {
        return filled;
    }

    @Override
    public void setFilled(long value) {
        this.filled = value;
    }

}
