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
		protected boolean transferPriority;
		protected LinkedList<ThreadState> waitList;
		protected ThreadState resourceOwner;

		protected int effectivePriority;

		PriorityQueue(boolean transferPriority)
		{
			this.transferPriority = transferPriority;
			this.waitList = new LinkedList<ThreadState>();
			this.resourceOwner = null;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());

			// Add the parameter thread to our waitList
			waitList.add(getThreadState(thread));

			// Add our priorityQueue to the parameter thread's waitResourceList
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread)
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			ThreadState threadState = getThreadState(thread);

			if (resourceOwner != null)
			{
				// Remove this waitQueue from the old resourceOwner's resourceList
				resourceOwner.resourceList.remove(this);
			}

			resourceOwner = threadState;

			threadState.acquire(this);
		}

		public KThread nextThread()
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// Iterate through the waitList and choose the one with the highest priority
			if (!waitList.isEmpty())
			{
				ThreadState largestPriority = waitList.get(0);

				for (ThreadState currentThread: waitList)
				{
					if (currentThread.priority > largestPriority.priority)
					{
						largestPriority = currentThread;
					}
				}

				waitList.remove(largestPriority);

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

			// Iterate through the waitList and choose the one with the highest priority
			if (!waitList.isEmpty())
			{
				ThreadState largestPriority = waitList.get(0);

				for (ThreadState currentThread: waitList)
				{
					if (currentThread.getEffectivePriority() > largestPriority.priority)
					{
						largestPriority = currentThread;
					}
				}

				return largestPriority;
			}

			return null;
		}

		public int getEffectivePriority()
		{
			effectivePriority = priorityMinimum;

			if (!waitList.isEmpty() && transferPriority)
			{
				ThreadState largestThread = waitList.get(0);

				for (ThreadState currentThread: waitList)
				{
					if (currentThread.priority > largestThread.priority)
					{
						largestThread = currentThread;
					}
				}

				effectivePriority = largestThread.priority;
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

		protected int effectivePriority = 0;
		protected int resourceSize = 0;

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

			effectivePriority = priority;

			if (!resourceList.isEmpty())
			{
				for (PriorityQueue currentQueue: resourceList)
				{
					effectivePriority = priority;

					int queuePriority = currentQueue.getEffectivePriority();

					System.out.println("Comparing " + effectivePriority + " to " + queuePriority);
					System.out.println(thread.getName() + " " + currentQueue.resourceOwner.thread.getName());

					if (queuePriority > effectivePriority)
					{
						effectivePriority = queuePriority;

						System.out.println("Setting EP to " + effectivePriority);
					}
				}
			}

			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param	priority	the new priority.
		 */
		public void setPriority(int priority) {
			Lib.assertTrue(Machine.interrupt().disabled());

			if (this.priority == priority)
			{
				return;
			}

			this.priority = priority;

			for (PriorityQueue pq: waitResourceList)
			{
				pq.getEffectivePriority();
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

			// Add the PriorityQueue into our waitResourceList
			waitResourceList.add(waitQueue);

//			if (waitQueue.resourceOwner != null)
//			{
//				// Update priority on owner of waitQueue
//				getThreadState(waitQueue.resourceOwner.thread).getEffectivePriority();
//			}

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

			resourceList.add(waitQueue);
			this.waitResourceList.remove(waitQueue);

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