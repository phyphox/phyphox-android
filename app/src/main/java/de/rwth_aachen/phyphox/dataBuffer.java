package de.rwth_aachen.phyphox;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

//Databuffer class
//Each databuffer can be identified by a name (mapped in phyphoxExperiment class)
//The actual databuffer is implemented as a BlockingQueue, but we keep track of a maximum size ourselves.

public class dataBuffer implements Serializable {
    public String name; //The key name
    private BlockingQueue<Double> buffer; //The actual buffer
    public int size; //The target size
    public double value; //The last added value for easy access and graceful returning NaN for empty buffers
    public boolean isStatic = false; //If set to static, this buffer should only be filled once and cannot be cleared thereafter

    //Contructor. Set key name and target size.
    protected dataBuffer(String name, int size) {
        this.size = size;
        this.name = name;
        this.buffer = new ArrayBlockingQueue<>(size);
        this.value = Double.NaN;
    }

    //Append a value to the buffer.
    public void append(double value) {
        this.value = value; //Update last value
        if (buffer.size()+1 > this.size) //If the buffer becomes larger than the target size, remove the first item (queue!)
            buffer.poll();
        buffer.add(value);
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

    //Delete all data and set last item to NaN (if not static)
    public void clear() {
        if (isStatic)
            return;
        buffer.clear();
        value = Double.NaN;
    }

    //Retrieve the iterator of the BlockingQueue
    public Iterator getIterator() {
        return buffer.iterator();
    }

    //Get all values as a double array
    public Double[] getArray() {
        Double ret[] = new Double[buffer.size()];
        return buffer.toArray(ret);
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
}