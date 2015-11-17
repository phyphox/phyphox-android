package de.rwth_aachen.phyphox;

import android.util.Log;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class dataBuffer implements Serializable {
    public String name;
    private BlockingQueue<Double> buffer;
    public int size;
    public double value;
    public boolean isStatic = false;

    protected dataBuffer(String name, int size) {
        this.size = size;
        this.name = name;
        this.buffer = new ArrayBlockingQueue<>(size);
        this.value = Double.NaN;
    }

    public void append(double value) {
        this.value = value;
        if (buffer.size()+1 > this.size)
            buffer.poll();
        buffer.add(value);
    }

    public void append(double value[], int count) {
        for (int i = 0; i < count; i++)
            append(value[i]);
    }

    public void append(short value[], int count) {
        for (int i = 0; i < count; i++)
            append((double)value[i]/(double)Short.MAX_VALUE);
    }

    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    public void clear() {
        if (isStatic)
            return;
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

    public short[] getShortArray() {
        short ret[] = new short[buffer.size()];
        Iterator it = getIterator();
        int i = 0;
        while (it.hasNext()) {
            ret[i] = (short)(((double)it.next())*(Short.MAX_VALUE));
            i++;
        }
        return ret;
    }
}