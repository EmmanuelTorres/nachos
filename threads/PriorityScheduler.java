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
		// Determines whether or not effective priority matters
		protected boolean transferPriority;

		// The list of resources waiting inside this PriorityQueue
		protected LinkedList<ThreadState> waitingResources;

		// The resource that is the owner of this PriorityQueue
		protected ThreadState resourceHolder;

		// Determines whether or not we should update our effectivePriority
		protected boolean updateNeeded;

		// The "cached" effective priority
		protected int effectivePriority;

		// Set all the values declared above to their initial states
		PriorityQueue(boolean transferPriority)
		{
			this.transferPriority = transferPriority;
			waitingResources = new LinkedList<ThreadState>();
			resourceHolder = null;
			updateNeeded = false;
			effectivePriority = priorityMinimum;
		}

		public void waitForAccess(KThread thread)
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// Finds the ThreadState belonging to this KThread thread
			ThreadState threadState = getThreadState(thread);

			// Add the parameter thread to our waitingResources
			waitingResources.add(threadState);

			// Add our priorityQueue to the parameter thread's waitResourceList
			threadState.waitForAccess(this);
		}

		public void acquire(KThread thread)
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			ThreadState oldOwner = resourceHolder;

			// If the old owner is not null
			if (oldOwner != null)
			{
				// Remove ourselves from the old owner's resourceList
				oldOwner.resourceList.remove(this);

				oldOwner.setPriorityFlag();
			}

			// Get the ThreadState of the new owner, the KThread thread in the parameter
			ThreadState newOwner = getThreadState(thread);

			// Set the new resourceHolder as the newly created ThreadState
			resourceHolder = newOwner;

			// Acquire ourselves into the new owner's resourceList
			newOwner.acquire(this);
		}

		public KThread nextThread()
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// Get the thread with the largest priority belonging to this PriorityQueue
			ThreadState largestPriority = pickNextThread();

			// If it isn't null
			if (largestPriority != null)
			{
				// Remove this ThreadState from our list of waitingResources
				waitingResources.remove(largestPriority);

				// Acquire this new ThreadState as the new owner
				acquire(largestPriority.thread);

				return largestPriority.thread;
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
		protected ThreadState pickNextThread()
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			ThreadState largestPriority = null;

			if (!waitingResources.isEmpty())
			{
				largestPriority = waitingResources.getFirst();

				for (ThreadState currentState: waitingResources)
				{
					if (currentState.getEffectivePriority() > largestPriority.priority)
					{
						largestPriority = currentState;
					}
				}
			}

			return largestPriority;
		}

		public int getEffectivePriority()
		{
			effectivePriority = priorityMinimum;

			if (transferPriority && updateNeeded)
			{
				updateNeeded = false;

				for (ThreadState currentThread: waitingResources)
				{
					int currentEffectivePriority = currentThread.getEffectivePriority();

					if (currentEffectivePriority > effectivePriority)
					{
						effectivePriority = currentEffectivePriority;
					}
				}
			}

			return effectivePriority;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());

			// implement me (if you want)
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

		// The resources this ThreadState is currently the owner of
		protected LinkedList<PriorityQueue> resourceList;

		// The resources this ThreadState are waiting on
		protected LinkedList<PriorityQueue> waitResourceList;

		// Determines whether or not we should update our effectivePriority
		protected boolean updateNeeded;

		// The "cached" effective priority of this ThreadState
		protected int effectivePriority = 0;

		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param	thread	the thread this state belongs to.
		 */
		public ThreadState(KThread thread)
		{
			this.thread = thread;
			this.resourceList = new LinkedList<PriorityQueue>();
			this.waitResourceList = new LinkedList<PriorityQueue>();
			this.updateNeeded = false;

			setPriority(priorityDefault);
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return	the priority of the associated thread.
		 */
		public int getPriority()
		{
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return	the effective priority of the associated thread.
		 */
		public int getEffectivePriority()
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// If we have no resources, the lowest possible effective priority we can have
			// is the priority belonging to our thread
			if (resourceList.isEmpty())
			{
				return priority;
			}

			//  If we need to override our priority
			if (updateNeeded)
			{
				updateNeeded = false;

				// Set our lowest possible priority
				effectivePriority = priority;

				// Find the highest priority of the resources we currently own
				for (PriorityQueue currentQueue: resourceList)
				{
					int currentEffectivePriority = currentQueue.getEffectivePriority();

					if (currentEffectivePriority > effectivePriority)
					{
						effectivePriority = currentEffectivePriority;
					}
				}
			}

			return effectivePriority;
		}

		// Updates the updateNeeded flag belonging to a PriorityQueue inside the resources we're waiting on
		// Essentially, this makes it so the next time we call PriorityQueue.getEffectivePriority() we'll force
		// a effectivePriority update if its set to true, otherwise we use the "cached" value
		public void setPriorityFlag()
		{
			if (!updateNeeded)
			{
				updateNeeded = true;

				for (PriorityQueue currentQueue : waitResourceList)
				{
					currentQueue.updateNeeded = true;
				}
			}
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			Lib.assertTrue(Machine.interrupt().disabled());

			if (this.priority == priority)
				return;

			// Set our priority
			this.priority = priority;

			// Update the effective priorities for each of the resources we're waiting on
			for (PriorityQueue currentQueue: waitResourceList)
			{
				currentQueue.updateNeeded = true;
			}

			// (Santosh Notes)
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
		public void waitForAccess(PriorityQueue waitQueue)
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// Add the waitQueue into our waitResourceList
			waitResourceList.add(waitQueue);

			// Remove it from our resourceList because we're no longer in possession of it
			resourceList.remove(waitQueue);

			// Update the effective priority of the waitQueue
			waitQueue.updateNeeded = true;

			// (Santosh Notes)
			// Update priority on owner of waitQueue
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
		public void acquire(PriorityQueue waitQueue)
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// Add the waitQueue into the resourceList
			resourceList.add(waitQueue);

			// Remove it from our waiting list of resources we want to own
			waitResourceList.remove(waitQueue);

			// Update the effective priority of the waitQueue
			waitQueue.updateNeeded = true;

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
	}
}