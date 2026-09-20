package de.rwth_aachen.phyphox;

import java.nio.FloatBuffer;

public class FloatBufferRepresentation {
    public FloatBuffer data;
    public int size;
    public int offset;
    transient public final Object lock = new Object();

    public FloatBufferRepresentation(FloatBuffer data, int offset, int size) {
        this.data = data;
        this.size = size;
        this.offset = offset;
    }
}
