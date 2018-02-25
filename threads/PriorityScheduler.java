package nachos.threads;

import nachos.machine.*;

import java.util.*;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the
 * thread that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has
 * the potential to
 * starve a thread if there's always a thread waiting with higher priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	private PriorityQueue[] priorities = new PriorityQueue[priorityMaximum + 1];

    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
    	// Initialize each PriorityQueue inside our array
    	for (PriorityQueue pq: priorities)
	    {
	    	pq = new PriorityQueue(true);
	    }
    }
    
    /**
     * Allocate a new priority thread queue.
     *
     * @param	transferPriority	<tt>true</tt> if this queue should
     *					transfer priority from waiting threads
     *					to the owning thread.
     * @return	a new priority thread queue.
     */
    public ThreadQueue newThreadQueue(boolean transferPriority) {
	return new PriorityQueue(transferPriority);
    }

    public int getPriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getPriority();
    }

    public int getEffectivePriority(KThread thread) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	return getThreadState(thread).getEffectivePriority();
    }

    public void setPriority(KThread thread, int priority) {
	Lib.assertTrue(Machine.interrupt().disabled());
		       
	Lib.assertTrue(priority >= priorityMinimum &&
		   priority <= priorityMaximum);
	
	getThreadState(thread).setPriority(priority);
    }

    public boolean increasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMaximum)
	    return false;

	setPriority(thread, priority+1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    public boolean decreasePriority() {
	boolean intStatus = Machine.interrupt().disable();
		       
	KThread thread = KThread.currentThread();

	int priority = getPriority(thread);
	if (priority == priorityMinimum)
	    return false;

	setPriority(thread, priority-1);

	Machine.interrupt().restore(intStatus);
	return true;
    }

    /**
     * The default priority for a new thread. Do not change this value.
     */
    public static final int priorityDefault = 1;
    /**
     * The minimum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMinimum = 0;
    /**
     * The maximum priority that a thread can have. Do not change this value.
     */
    public static final int priorityMaximum = 7;    

    /**
     * Return the scheduling state of the specified thread.
     *
     * @param	thread	the thread whose scheduling state to return.
     * @return	the scheduling state of the specified thread.
     */
    protected ThreadState getThreadState(KThread thread) {
	if (thread.schedulingState == null)
	    thread.schedulingState = new ThreadState(thread);

	return (ThreadState) thread.schedulingState;
    }

    /**
     * A <tt>ThreadQueue</tt> that sorts threads by priority.
     */
    protected class PriorityQueue extends ThreadQueue
    {
    	// Comparator needed for holding a PriorityQueue based on priority
    	Comparator<ThreadState> comparator;
    	java.util.PriorityQueue<ThreadState> priorityQueue;
    	// Used to signify who holds this currently Priority Queue
	    // Can be overridden, which is why we use this instead of queue.peek()/poll()
    	ThreadState queueHolder;

		PriorityQueue(boolean transferPriority)
		{
		    this.transferPriority = transferPriority;
		    comparator = new PriorityComparator();
		    priorityQueue = new java.util.PriorityQueue<ThreadState>(10, comparator);
		}

		public void waitForAccess(KThread thread) {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
		    Lib.assertTrue(Machine.interrupt().disabled());
		    getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
		    Lib.assertTrue(Machine.interrupt().disabled());

		    return priorityQueue.poll().thread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
		    return priorityQueue.peek();
		}

		public void print() {
		    Lib.assertTrue(Machine.interrupt().disabled());

		    // implement me (if you want)
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 */
		public boolean transferPriority;

		public class PriorityComparator implements Comparator<ThreadState>
		{
			@Override
			public int compare(ThreadState o1, ThreadState o2)
			{
				if (o1.priority == o2.priority)
				{
					return 0;

				}
				else if (o1.priority > o2.priority)
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

    /**
     * The scheduling state of a thread. This should include the thread's
     * priority, its effective priority, any objects it owns, and the queue
     * it's waiting for, if any.
     *
     * @see	nachos.threads.KThread#schedulingState
     */
    protected class ThreadState {
	    /** The thread with which this object is associated. */
	    protected KThread thread;
	    /** The priority of the associated thread. */
	    protected int priority;
	    protected int effectivePriority;

	    // Holds the resources belonging to the ThreadState in order to
	    // count the effective priority
	    ArrayList<ThreadState> resourceList;

		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
		    this.thread = thread;
		    resourceList = new ArrayList<ThreadState>();

		    setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority() {
		    return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// This function needs to have atomicity so we aren't iterating through
			// a changing list
			Machine.interrupt().disable();
		    // The sum of effective priorities depending on this thread
			int sum = 0;

			// Iterates through all a ThreadState's resources
			// A resource is a thread that depends on this one
			for (ThreadState threadState: resourceList)
			{
				// Updates any thread that even remotely depends on this one
				sum += threadState.getEffectivePriority();
			}

			// Set our effectivePriority variable
			effectivePriority = sum;

			// Re-enable the machine interrupts
			Machine.interrupt().enable();

			// Return the effective priority of this thread
		    return sum;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			// ThreadState will hold its age if we mistakenly try to
			// set its priority to a value it already holds
			if (this.priority == priority)
			{
				return;
			}

			// Remove this ThreadState from the old priority queue
			priorities[this.priority].priorityQueue.remove(this);

			// Update the priority belonging to this ThreadState
		    this.priority = priority;

		    // Update the effective priority as well as normal priority
		    this.effectivePriority = getEffectivePriority();

		    // Add this ThreadState to its new priority
		    priorities[this.priority].priorityQueue.add(this);

		    // Check the priority if it needs to carry out priority donation at this point
			// in the sense of all threads that want to give it
			// Update priority is something we want to consider
			// Remove thread from queue, put inside new queue of array
			// Should call update priority in here

			// Might want to maintain queues we're waiting on
			// Check to see if they're not null, remove from that queue
			// and then set our priority and add ourselves back into this queue
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the
		 * resource guarded by <tt>waitQueue</tt>. This method is only called
		 * if the associated thread cannot immediately obtain access.
		 *
		 * @param	waitQueue	the queue that the associated thread is
		 *				now waiting on.
		 *
		 * @see	nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// Update priority on owner of waitQueue

			// If the wait queue is not empty, aka it already has threads
			// waiting on the locked resource then
			if (!waitQueue.priorityQueue.isEmpty())
			{
				// We enqueue ourselves onto this queue and wait fairly
				waitQueue.priorityQueue.add(this);
			}
			// Otherwise the wait queue is empty and we just set the thread to ready
			else
			{
				this.thread.ready();
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see	nachos.threads.ThreadQueue#acquire
		 * @see	nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			Machine.interrupt().disable();

			// Set ourselves as the owner of this PriorityQueue
			waitQueue.queueHolder = this;

			// At this point, nobody else should be holding the resource waitQueue
			// We want to update our resources(?)

			// Set the holder of waitQueue to our ThreadState

			Machine.interrupt().enable();
			// atomicity
			// nobody else is holding this resource (waitQueue)
			// Thread wants to get a hold of resource, call will only happen
			// when interrupts are disabled and the waitQueue associated with a resource is empty
			// If we're maintaining other stuff, like what thread is holding priority queue,
			// Thread can hold multiple resource queues
			// Update resources
			// Set holder to ThreadState
			// Have a queue, it holds PriorityQueues aka resources
		}
    }
}
