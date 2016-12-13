package org.freeticks;

public interface OrderType {
    int BUY         = 0x0001;
    int SELL        = 0x0002;

    long IOC        = 0;
    long GTC        = -1;
}
