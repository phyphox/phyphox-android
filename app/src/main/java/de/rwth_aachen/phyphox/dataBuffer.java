package de.rwth_aachen.phyphox;

import android.util.Log;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

//Databuffer class
//Each databuffer can be identified by a name (mapped in phyphoxExperiment class)
//The actual databuffer is implemented as a BlockingQueue, but we keep track of a maximum size ourselves.

public class dataBuffer implements Serializable {
    public String name; //The key name
    private BlockingQueue<Double> buffer; //The actual buffer (will be initialized as ArrayBlockingQueue which is Serializable)
    public int size; //The target size
    public double value; //The last added value for easy access and graceful returning NaN for empty buffers
    public boolean isStatic = false; //If set to static, this buffer should only be filled once and cannot be cleared thereafter
    public boolean untouched = true;
    public double init = Double.NaN;

    transient private floatBufferRepresentation floatCopy = null; //If a float copy has been requested, we keep it around as it will probably be requested again...
    private int floatCopyCapacity = 0;

    private double min = Double.NaN;
    private double max = Double.NaN;

    //Contructor. Set key name and target size.
    protected dataBuffer(String name, int size) {
        this.size = size;
        this.name = name;
        if (size > 0)
            this.buffer = new ArrayBlockingQueue<>(size);
        else
            this.buffer = new LinkedBlockingQueue<>();
        this.value = Double.NaN;
    }

    //Append a value to the buffer.
    public void append(double value) {
        untouched = false;
        this.value = value; //Update last value
        if (this.size > 0 && buffer.size()+1 > this.size) { //If the buffer becomes larger than the target size, remove the first item (queue!)
            buffer.poll();
            min = Double.NaN;
            max = Double.NaN;
            if (floatCopy != null) {
                synchronized (floatCopy.lock) {
                    floatCopy.offset++;
                    floatCopy.size--;
                }
            }

        }
        buffer.add(value);
        if (!Double.isNaN(min))
            min = Math.min(min, value);
        if (!Double.isNaN(max))
            max = Math.max(max, value);

        if (floatCopy == null)
            return;
        synchronized (floatCopy.lock) {
            if (floatCopyCapacity < floatCopy.offset + floatCopy.size + 1) {
                if (floatCopyCapacity < buffer.size() * 2)
                    floatCopyCapacity *= 2;
                FloatBuffer newData = ByteBuffer.allocateDirect(floatCopyCapacity * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
                floatCopy.data.position(floatCopy.offset);
                newData.put(floatCopy.data);
                floatCopy.data = newData;
                floatCopy.offset = 0;
            }
            floatCopy.data.put(floatCopy.offset + floatCopy.size, (float) value);
            floatCopy.size++;
        }
    }

    //Get the number of elements actually filled into the buffer
    public int getFilledSize() {
        return buffer.size();
    }

    //Append a double-array with [count] entries.
    public void append(Double value[], Integer count) {
        for (int i = 0; i < count; i++)
            append(value[i]);
    }

    //Append a short-array with [count] entries. This will be scaled to [-1:+1] and is used for audio data
    public void append(short value[], int count) {
        for (int i = 0; i < count; i++)
            append((double)value[i]/(double)Short.MAX_VALUE); //Normalize to [-1:+1] and append
    }

    //Wrapper function to set this buffer's static-state
    public void setStatic(boolean isStatic) {
        this.isStatic = isStatic;
    }

    //Wrapper function to set this buffer's static-state
    public void setInit(double init) {
        this.init = init;
        if (!Double.isNaN(init))
            this.append(init);
    }

    //Delete all data and set last item to NaN (if not static)
    public void clear(boolean reset) {
        if (isStatic)
            return;
        if (reset)
            untouched = true;
        else
            untouched = false;
        buffer.clear();
        value = Double.NaN;
        if (floatCopy != null) {
            synchronized (floatCopy.lock) {
                floatCopy = null;
            }
        }
        min = Double.NaN;
        max = Double.NaN;

        if (!Double.isNaN(init))
            this.append(init);
    }

    //Retrieve the iterator of the BlockingQueue
    public Iterator<Double> getIterator() {
        return buffer.iterator();
    }

    //Get all values as a double array
    public Double[] getArray() {
        Double ret[] = new Double[buffer.size()];
        return buffer.toArray(ret);
    }

    //Get all values as a double array
    public floatBufferRepresentation getFloatBuffer() {
        int n = buffer.size();
        if (n == 0)
            return new floatBufferRepresentation(null, 0, 0);

        if (floatCopy == null) {
            FloatBuffer data = ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
            floatCopyCapacity = n;
            Iterator it = getIterator();
            int i = 0;
            while (it.hasNext() && i < n) {
                data.put((float) (double) it.next());
                i++;
            }
            floatCopy = new floatBufferRepresentation(data, 0, n);
        }
        return floatCopy;
    }

    //Get all values as a short array. The data will be scaled so that (-/+)1 matches (-/+)Short.MAX_VALUE, used for audio data
    public short[] getShortArray() {
        short ret[] = new short[buffer.size()];
        Iterator it = getIterator();
        int i = 0;
        while (it.hasNext()) {
            ret[i] = (short)(((double)it.next())*(Short.MAX_VALUE)); //Rescale data to short range
            i++;
        }
        return ret;
    }

    public dataBuffer copy() {
        dataBuffer db = new dataBuffer(this.name, this.size);
        db.append(this.getArray(), this.getFilledSize());
        db.isStatic = this.isStatic;
        return db;
    }

    public double getMin() {
        if (!Double.isNaN(min)) {
            return min;
        }

        if (buffer.size() == 0)
            return Double.NaN;

        min = Collections.min(buffer, new minComparator());

        return min;
    }

    public double getMax() {
        if (!Double.isNaN(max)) {
            return max;
        }

        if (buffer.size() == 0)
            return Double.NaN;

        max = Collections.max(buffer, new maxComparator());

        return max;
    }

    class minComparator implements Comparator<Double> {
        public int compare(Double a, Double b) {
            if (Double.isNaN(a) || Double.isInfinite(a))
                return 1;
            if (Double.isNaN(b) || Double.isInfinite(b))
                return -1;
            return Double.compare(a, b);
        }
    }

    class maxComparator implements Comparator<Double> {
        public int compare(Double a, Double b) {
            if (Double.isNaN(a) || Double.isInfinite(a))
                return -1;
            if (Double.isNaN(b) || Double.isInfinite(b))
                return 1;
            return Double.compare(a, b);
        }
    }
}

class floatBufferRepresentation {
    FloatBuffer data;
    int size;
    int offset;
    transient public final Object lock = new Object();

    floatBufferRepresentation(FloatBuffer data, int offset, int size) {
        this.data = data;
        this.size = size;
        this.offset = offset;
    }
}