package nachos.threads;

import nachos.machine.*;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

    PriorityQueue<WaitingThread> waitingQueue = new PriorityQueue<WaitingThread>(10, new WaitingComparator());

    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() 
    {
    	// Disable machine interrupts to allow for atomicity
    	Machine.interrupt().disable();

    	while (!waitingQueue.isEmpty() &&
			    Machine.timer().getTime() >= waitingQueue.peek().wakeUpTime)
	    {
	    	waitingQueue.poll().thread.ready();
	    }

	    KThread.currentThread().yield();

    	// Re-enable machine interrupts
       Machine.interrupt().enable();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long offset)
    {
	    // Disable machine interrupts to allow for atomicity
	    Machine.interrupt().disable();

	    // The minimum amount the machine time should be at before this thread is woken
	    long wakeTime = Machine.timer().getTime() + offset;

	    // Create a new thread with this value in mind
	    // TODO: Replace this with a map instead of creating a new class
        WaitingThread wt = new WaitingThread( wakeTime, KThread.currentThread() );

        waitingQueue.add(wt);

        KThread.currentThread().sleep();

	    // Re-enable machine interrupts
	    Machine.interrupt().enable();
    }

    public class WaitingThread
    {
        long wakeUpTime;
        KThread thread;

        WaitingThread( long wakeUpTime, KThread thread )
        {
            this.wakeUpTime = wakeUpTime;
            this.thread = thread;
        }
    }

    public class WaitingComparator implements Comparator<WaitingThread>
    {
        @Override
        public int compare(WaitingThread o1, WaitingThread o2)
        {
            if (o1.wakeUpTime == o2.wakeUpTime)
            {
                return 0;

            }
            else if (o1.wakeUpTime < o2.wakeUpTime)
            {
                return -1;
            }
            else
            {
                return 1;
            }
        }
    }
}
