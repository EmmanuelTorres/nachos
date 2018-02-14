package nachos.threads;

import nachos.machine.*;

import java.util.LinkedList;
import java.util.Queue;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
    }

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

	    // Release the condition lock we have on this condition
	    conditionLock.release();

		// This function needs atomicity so we lock the machine
		Machine.interrupt().disable();

		// Add a new ThreadQueue to the waitQueue
		waitQueue.add(ThreadedKernel.scheduler.newThreadQueue(false));

		// Put the thread to sleep while we wait
		KThread.sleep();

	    // Re-enable machine interrupts
	    Machine.interrupt().enable();

		// Acquire conditions for the above thread
	    conditionLock.acquire();
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// If the wait queue is not empty
		if (!waitQueue.isEmpty())
		{
			// Get the first thread queue inside that list
			ThreadQueue oldestThread = waitQueue.removeFirst();

			// Disable machine interrupts
			Machine.interrupt().disable();

			// Wake the first thread within that thread queue
			oldestThread.nextThread().ready();

			// Re-enable machine interrupts
			Machine.interrupt().enable();
		}
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		// While the wait queue is not empty
		while (!waitQueue.isEmpty())
		{
			// Wake every single first thread until no threads remain
			wake();
		}
    }

    private Lock conditionLock;
    private LinkedList<ThreadQueue> waitQueue;
}
