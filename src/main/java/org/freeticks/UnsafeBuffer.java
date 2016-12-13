package org.freeticks;

import net.openhft.chronicle.core.UnsafeMemory;
import net.openhft.chronicle.core.annotation.ForceInline;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;

public class UnsafeBuffer {
    public static final boolean BOUNDS = false;

    protected final long headAddress;
    protected final long tailAddress;
    protected final long elementSize;
    protected final DirectBuffer bytes;

    public UnsafeBuffer(long capacity, long elementSize) {
        this.elementSize = elementSize;
        int cap = (int)(elementSize*capacity);
        this.bytes =  ((DirectBuffer)ByteBuffer.allocateDirect(cap));
        this.headAddress = bytes.address();
        this.tailAddress = bytes.address() + cap;
    }

    @ForceInline
    public final long getLong(long addr) {
        if(BOUNDS)
            if(addr< headAddress || addr + Long.BYTES> tailAddress)
                throw new IndexOutOfBoundsException();

        return UnsafeMemory.UNSAFE.getLong(addr);
    }

    @ForceInline
    public final void putLong(long addr, long value) {
        if(BOUNDS)
            if( addr< headAddress || addr + Long.BYTES> tailAddress)
                throw new IndexOutOfBoundsException();
        UnsafeMemory.UNSAFE.putLong(addr, value);
    }

    @ForceInline
    public final int getInt(long addr) {
        if(BOUNDS)
            if(addr< headAddress || addr + Integer.BYTES> tailAddress)
                throw new IndexOutOfBoundsException();

        return UnsafeMemory.UNSAFE.getInt(addr);
    }

    @ForceInline
    public final void putInt(long addr, int value) {
        if(BOUNDS)
            if(addr< headAddress || addr + Integer.BYTES> tailAddress)
                throw new IndexOutOfBoundsException();

        UnsafeMemory.UNSAFE.putInt(addr, value);
    }
}
