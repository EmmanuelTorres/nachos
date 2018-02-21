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
    Condition needSpeaker;
    Condition needListener;
    Lock lock;
    int message;
    boolean isSpeaking;
    boolean isListening;
    boolean ready;

    public Communicator() {
        lock = new Lock();
	needSpeaker = new Condition(lock);
	needListener = new Condition(lock);
        isSpeaking = false;
	isListening = false;
	message = 0;
	ready = false;
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
	isSpeaking = true;
	if(isListening == false) {
            needListener.sleep();
	}
        message = word;
	ready = true;
        needSpeaker.wake();
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
        isListening = true;
	if(isSpeaking == false) {
            needSpeaker.sleep();
	}
        needListener.wake();
        int word = message;
        reset();
        return word;
    }

    private void reset() {
        isSpeaking = false;
	isListening = false;
	message = 0;
	ready = false;
	lock.release();
	return;
    }
}
