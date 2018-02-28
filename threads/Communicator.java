package nachos.threads;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    Condition speaker;
    Condition listener;
    Lock lock;
    Integer buffer;
    int speakers;
    int listeners;

    public Communicator() {
        lock = new Lock();
        speaker = new Condition(lock);
        listener = new Condition(lock);
        buffer = null;
        speakers = 0;
        listeners = 0;
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
        lock.acquire();
        speakers++;

        while(listeners == 0 || buffer != null) {
            speaker.sleep();
        }

        buffer = (Integer)word;
        listener.wake();

        speakers--;
        lock.release();
        return;
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
        lock.acquire();
        listeners++;

	while(buffer == null) {
            speaker.wake();
            listener.sleep();
        }

        int word = (int)buffer;
        buffer = null;

        listeners--;
        lock.release();
        return word;
    }
}
