package de.rwth_aachen.phyphox;

import android.util.Log;

import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class dataBuffer {
    public String name;
    private BlockingQueue<Double> buffer;
    public int size;
    public double value;

    protected dataBuffer(String name, int size) {
        this.size = size;
        this.name = name;
        this.buffer = new ArrayBlockingQueue<Double>(size);
    }

    public void append(double value) {
        this.value = value;
        if (buffer.size()+1 > this.size)
            buffer.poll();
        buffer.add(value);
    }

    public void clear() {
        buffer.clear();
        value = Double.NaN;
    }

    public Iterator getIterator() {
        return buffer.iterator();
    }

    public Double[] getArray() {
        Double ret[] = new Double[buffer.size()];
        return buffer.toArray(ret);
    }
}