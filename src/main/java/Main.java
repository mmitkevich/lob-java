import java.util.Arrays;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class Main {

    static class Level {
        public long size;
        public long price;
        public Level(long price, long size) {
            this.price = price;
            this.size = size;
        }
        public long getPrice() {
            return price;
        }
    }

    static class Order extends Level {
        public long flags;

        public Order(long price, long size) {
            super(price, size);
            if(size>0)
                this.flags|=1;
            else if(size<0)
                this.flags|=2;
        }

    }

    public static void main(String[] args) {
        //Stream<Order> ordersStream = IntStream.range(0, 500000000).mapToObj(i -> new Order(i*i, i) );
        //System.out.println("Sum=" + ordersStream.map(Order::getPrice).reduce(0L, (a,b)->a+b));

        LongStream longStream = IntStream.range(0, 1000_000_000).mapToLong(i -> (long)i );
        System.out.println("Long.Sum=" + longStream.map(i -> i+1).reduce(0L, (a,b)->a+b));

    }
}
