package de.rwth_aachen.phyphox;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

//dataInput wraps all data-containers (currently only dataBuffer) and constant values as possible
// inputs. This allows analysis modules to access constant values as if they were buffers.

public class dataInput implements Serializable {
    boolean isBuffer = false;
    double value = Double.NaN;
    dataBuffer buffer = null;
    boolean clearAfterRead = true;

    //Get value
    public double getValue() {
        if (isBuffer)
            return buffer.value;
        else
            return value;
    }

    //Constructor if this should contain a buffer
    protected dataInput(dataBuffer buffer, boolean clear) {
        this.clearAfterRead = clear;
        isBuffer = true;
        this.buffer = buffer;
    }

    //Constructor if this should contain a constant value
    protected dataInput(double value) {
        isBuffer = false;
        this.value = value;
    }

    //Get the number of elements actually filled into the buffer
    public int getFilledSize() {
        if (isBuffer)
            return buffer.getFilledSize();
        else
            return 1;
    }

    //Retrieve the iterator of the BlockingQueue
    public Iterator getIterator() {
        if (isBuffer)
            return buffer.getIterator();
        else
            return null;
    }

    //Get all values as a double array
    public Double[] getArray() {
        if (isBuffer) {
            return buffer.getArray();
        } else {
            Double ret[] = new Double[1];
            ret[0] = value;
            return ret;
        }
    }

    //Get all values as a short array. The data will be scaled so that (-/+)1 matches (-/+)Short.MAX_VALUE, used for audio data
    public short[] getShortArray() {
        if (isBuffer) {
            return buffer.getShortArray();
        } else {
            short ret[] = new short[1];
            ret[0] = (short) (value * (Short.MAX_VALUE)); //Rescale data to short range;
            return ret;
        }
    }

    public void clear() {
        buffer.clear();
    }

    protected dataInput copy() {
        if (this.isBuffer) {
            return new dataInput(this.buffer.copy(), this.clearAfterRead);
        } else {
            return new dataInput(this.value);
        }
    }
}