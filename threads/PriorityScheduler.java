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
    /**
     * Allocate a new priority scheduler.
     */
    public PriorityScheduler() {
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

		    if (!priorityQueue.isEmpty())
		    {
			    return priorityQueue.poll().thread;
		    }

		    return null;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return	the next thread that <tt>nextThread()</tt> would
		 *		return.
		 */
		protected ThreadState pickNextThread() {
			if (priorityQueue.isEmpty())
			{
				return priorityQueue.peek();
			}

			return null;
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
	    protected int resourceSize = 0;

	    // Holds the resources belonging to the ThreadState in order to
	    // count the effective priority
	    private java.util.PriorityQueue<PriorityQueue> resourceList;

		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
		    this.thread = thread;
		    resourceList = new java.util.PriorityQueue<PriorityQueue>(10, new ResourceComparator());

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

			// Set the highest priority belonging to this thread, as a default, to the minimum
			int highestPriority = priorityMinimum;

			// Iterate through all resources this Thread belongs to only if the sizes
			// don't match
			// This works as a pseudo-cache to reduce computations
			if (resourceList.size() != resourceSize)
			{
				for (PriorityQueue pq: resourceList)
				{
					if (!pq.priorityQueue.isEmpty())
					{
						// Get the highest priority
						if (pq.priorityQueue.peek().priority > highestPriority)
						{
							highestPriority = pq.priorityQueue.peek().priority;
						}
					}
				}

				// Save the highest priority for later
				effectivePriority = highestPriority;

				// Save the size for future comparisons
				resourceSize = resourceList.size();
			}
			else
			{
				// If the sizes are the same, get the old values
				highestPriority = effectivePriority;
			}

			// Re-enable machine interrupts
			Machine.interrupt().enable();

			return highestPriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			// Allow for atomicity for this function
			Machine.interrupt().disable();

			// ThreadState will hold its age if we mistakenly try to
			// set its priority to a value it already holds
			if (this.priority == priority)
			{
				return;
			}

			// Set the priority to the parameter value
			this.priority = priority;

			// Forces a refresh on priorities
			resourceSize = -1;
			getEffectivePriority();

			Machine.interrupt().enable();

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
			Lib.assertTrue(Machine.interrupt().disabled());

			// Add ourselves into the waitQueue for a resource
			waitQueue.priorityQueue.add(this);

			// Update priority on owner of waitQueue
			// (Recommended by Santosh)
			// waitQueue.priorityQueue.peek().getEffectivePriority();
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

			Lib.assertTrue(Machine.interrupt().disabled());

			if (waitQueue.queueHolder != this)
			{
				// Set ourselves to the owner of this queue
				waitQueue.queueHolder = this;
			}

			// Add this queue to our list of resources
			resourceList.add(waitQueue);

			// (Santosh notes)
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

	    public class ResourceComparator implements Comparator<PriorityQueue>
	    {
		    @Override
		    public int compare(PriorityQueue o1, PriorityQueue o2)
		    {
		    	if (o1.priorityQueue.isEmpty() || o2.priorityQueue.isEmpty())
			    {
			    	return 1;
			    }

		    	if (o1.priorityQueue.peek().priority == o2.priorityQueue.peek().priority)
			    {
			    	return 0;
			    }
			    else if (o1.priorityQueue.peek().priority < o2.priorityQueue.peek().priority)
			    {
			    	return 1;
			    }
			    else
			    {
			    	return -1;
			    }
		    }
	    }
    }
}
