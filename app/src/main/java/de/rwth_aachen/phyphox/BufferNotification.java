package de.rwth_aachen.phyphox;

public interface BufferNotification {
    void notifyUpdate(boolean clear, boolean reset); //Notify that a buffer has changed. Also notify if the buffer has been cleared (for example during
}
