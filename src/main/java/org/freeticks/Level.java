package org.freeticks;

import java.util.List;

public class Level {
    private long volume;
    private long price;

    public Level(long volume, long price) {
        this.volume = volume;
        this.price = price;
    }

    public long price() {
        return price;
    }

    public long volume() {
        return volume;
    }

    @Override
    public String toString() {
        return String.format("%d->%d",price, volume);
    }

    @Override
    public boolean equals(Object obj) {
        if(obj==null) return false;
        if(obj.getClass()!=getClass()) return false;
        Level other = (Level)obj;
        if(price!=other.price)
            return false;
        if(volume !=other.volume)
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        return (int)((price^ volume)^((price^ volume)>>32));
    }
}
