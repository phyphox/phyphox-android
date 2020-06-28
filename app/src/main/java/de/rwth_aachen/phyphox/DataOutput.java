package de.rwth_aachen.phyphox;

import java.io.Serializable;
import java.util.Iterator;

//dataOutput wraps all data-containers (currently only dataBuffer) as possible outputs.
//Also allowing to store additional parameters (like clearing the buffer before filling it)

public class DataOutput implements Serializable {
    DataBuffer buffer = null;
    boolean clearBeforeWrite = true;

    //Get value
    public double getValue() {
        return buffer.value;
    }

    //Constructor with specified clear attribute
    protected DataOutput(DataBuffer buffer, boolean clear) {
        this.clearBeforeWrite = clear;
        this.buffer = buffer;
    }

    //Get the number of elements actually filled into the buffer
    public int getFilledSize() {
        return buffer.getFilledSize();
    }

    //Retrieve the iterator of the BlockingQueue
    public Iterator getIterator() {
        return buffer.getIterator();
    }

    //Get all values as a double array
    public Double[] getArray() {
        return buffer.getArray();
    }

    //Get all values as a short array. The data will be scaled so that (-/+)1 matches (-/+)Short.MAX_VALUE, used for audio data
    public short[] getShortArray() {
        return buffer.getShortArray();
    }

    //Append to the buffer
    public void append(double value) {
        buffer.append(value);
    }

    public void append(Double value[], Integer count) {
        buffer.append(value, count);
    }

    public boolean isStatic() {
        return buffer.isStatic;
    }

    public void clear(boolean reset) {
        buffer.clear(reset);
    }

    public int size() {
        return buffer.size;
    }

    public void markSet() {
        buffer.markSet();
    }
}
