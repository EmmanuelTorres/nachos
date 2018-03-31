package nachos.threads;

import java.util.LinkedList;
import java.util.Random;

import nachos.machine.*;

public class LotteryScheduler extends Scheduler
{
	// The minimum priority is increased from 0 to 1
	private static final int MIN_PRIORITY = 1;

	// The maximum priority is increased to 2,147,483,647 from 7
	private static final int MAX_PRIORITY = Integer.MAX_VALUE;

	/*
	 * The only difference between LotteryScheduler and PriorityScheduler is the way
	 * getThreadState() is called. Since we're using a custom ThreadState, LotteryThreadState,
	 * we need to replace all the old calls with the new ones.
	 */
	public LotteryScheduler()
	{
	}

	@Override
	public ThreadQueue newThreadQueue(boolean transferPriority)
	{
		return new LotteryQueue(transferPriority);
	}

	@Override
	public int getPriority(KThread thread)
	{
		Lib.assertTrue(Machine.interrupt().disabled());

		return getLotteryThreadState(thread).getTickets();
	}

	@Override
	public int getEffectivePriority(KThread thread)
	{
		Lib.assertTrue(Machine.interrupt().disabled());

		return getLotteryThreadState(thread).getEffectiveTickets();
	}

	@Override
	public void setPriority(KThread thread, int priority)
	{
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= MIN_PRIORITY && priority <= MAX_PRIORITY);

		LotteryThreadState lotteryThreadState = getLotteryThreadState(thread);

		// Set the tickets to the priority
		// LotteryThreadState does NOT inherit the priority of a ThreadState
		if (priority != lotteryThreadState.getTickets())
		{
			lotteryThreadState.setTickets(priority);
		}
	}

	/*
	 * The new function that will replace getThreadState()
	 * Allows us to use the new variables/functions in our LotteryThreadState class
	 */
	private LotteryThreadState getLotteryThreadState(KThread thread)
	{
		if (thread.schedulingState == null)
			thread.schedulingState = new LotteryThreadState(thread);

		return (LotteryThreadState) thread.schedulingState;
	}

	/*
	 * A queue that schedules based on a lottery instead of priority
	 *
	 */
	protected class LotteryQueue extends ThreadQueue
	{
		// The resources that are waitResourceList to become resourceHolders
		private LinkedList<LotteryThreadState> waitingResources = new LinkedList<LotteryThreadState>();

		// The ThreadState that currently owns this LotteryQueue
		private LotteryThreadState resourceHolder;

		// The total number of tickets that this LotteryQueue holds
		// A replacement value for effectivePriority
		private int totalEffectiveTickets;

		// Determines whether or not to use the lottery system
		// A replacement value for transferPriority
		private boolean transferTickets;

		LotteryQueue(boolean transferTickets)
		{
			this.transferTickets = transferTickets;
		}

		@Override
		public void waitForAccess(KThread thread)
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			getLotteryThreadState(thread).waitForAccess(this);
		}


		/*
		 * Implements a lottery system for choosing the next thread. The lottery works as such:
		 * A luckyTicketValue from 0 to the totalEffectiveTickets is chosen
		 * We iterate through waitingResources, decrementing the luckyTicketValue as we go along
		 * When the luckyTicketValue is <= 0, we choose the current thread in the iteration as the next thread
		 */
		@Override
		public KThread nextThread()
		{
			Lib.assertTrue(Machine.interrupt().disabled());

			// If there are no KThreads in the waitResourceList queue, the next thread is null
			if (waitingResources.isEmpty())
			{
				return null;
			}

			// Initialize a KThread to be returned
			KThread luckyThread = null;

			// Assign a lucky value of a ticket to a random number
			int luckyTicketValue = new Random().nextInt(totalEffectiveTickets)+1;

			// Iterate through all waitingResources
			for (LotteryThreadState currentThreadState : waitingResources)
			{
				// Decrement the lucky ticket value
				luckyTicketValue -= currentThreadState.getEffectiveTickets();

				// When the luckyTicketValue is reached, we return the thread that won
				if (luckyTicketValue <= 0)
				{
					luckyThread = currentThreadState.thread;
					currentThreadState.acquire(this);
					break;
				}
			}

			return luckyThread;
		}

		/*
		 * The workload completely relies on the LotteryThreadState rather than both LotteryQueue
		 * and LotteryThreadState (like how it does in the PriorityScheduler)
		 */
		@Override
		public void acquire(KThread thread)
		{
			Lib.assertTrue(Machine.interrupt().disabled());
			getLotteryThreadState(thread).acquire(this);
		}

		@Override
		public void print()
		{
			// Still not implementing this lol
		}

		/*
		 * Updates the amount of totalEffectiveTickets any time there is a change to waitingResources
		 * Note that this needs to be called manually when waitingResources is changed
		 */
		void updateEffectiveTickets()
		{
			totalEffectiveTickets = 0;

			for (LotteryThreadState currentThreadState : waitingResources)
			{
				totalEffectiveTickets += currentThreadState.getEffectiveTickets();
			}
		}

		/*
		 * When a LotteryThreadState is removed from waitResourceList, we need to update our totalEffectiveTickets
		 * and then update the LotteryQueues belonging to our resourceHolder
		 * This essentially replaces the flags system we had in PriorityScheduler
		 */
		void removeFromWaiting(LotteryThreadState lotteryThreadState)
		{
			if (waitingResources.remove(lotteryThreadState))
			{
				updateEffectiveTickets();

				if (resourceHolder != null)
				{
					resourceHolder.updateAllEffectiveTickets();
				}
			}
		}
	}

	private class LotteryThreadState
	{
		// The LotteryQueues this LotteryThreadState owns
		private LinkedList<LotteryQueue> resourceList = new LinkedList<LotteryQueue>();
		// The LotteryQueues this LotteryThreadState is waiting to own
		private LinkedList<LotteryQueue> waitResourceList = new LinkedList<LotteryQueue>();

		// The KThread this LotteryThreadState belongs to
		private KThread thread;

		// The lowest amount of tickets a LotteryThreadState can hold is 1
		private int tickets = MIN_PRIORITY;
		// The lowest amount of effectiveTickets a LotteryThreadState can hold is 1
		private int effectiveTickets = MIN_PRIORITY;

		LotteryThreadState(KThread thread)
		{
			this.thread = thread;
		}

		/*
		 * Returns the tickets belonging to this LotteryThreadState
		 */
		int getTickets()
		{
			return tickets;
		}

		/*
		 * Returns the tickets belonging to this LotteryThreadState
		 */
		int getEffectiveTickets()
		{
			return effectiveTickets;
		}

		/*
		 * Updates all the effectiveTickets on resources we own, and resources we're waiting on
		 * This is mainly called when a change in the lists has occurred, or when a change in
		 * the tickets throughout the lists has occurred
		 */
		private void updateAllEffectiveTickets()
		{
			effectiveTickets = tickets;

			// Iterate through each element in the list of resources we own
			for (LotteryQueue lotteryQueue: resourceList)
			{
				// If the lotteryQueue has transferTickets enabled
				if (lotteryQueue.transferTickets)
				{
					// We update our own effectiveTickets before continuing with updating the
					// effectiveTickets of the list of resources we're waiting on
					// THIS IS IMPORTANT.
					for (LotteryThreadState lotteryThreadState : lotteryQueue.waitingResources)
					{
						effectiveTickets += lotteryThreadState.effectiveTickets;
					}
				}
			}

			// After our own effectiveTickets has been updated, we can proceed with
			// updating the effectiveTickets of all the resources we're waiting on
			for (LotteryQueue lotteryQueue : waitResourceList)
			{
				lotteryQueue.updateEffectiveTickets();

				if (lotteryQueue.transferTickets && lotteryQueue.resourceHolder != null)
				{
					lotteryQueue.resourceHolder.updateAllEffectiveTickets();
				}
			}
		}

		/*
		 * Set the tickets belonging to this LotteryThreadState
		 */
		void setTickets(int tickets)
		{
			this.tickets = tickets;

			// Since a change in tickets has occurred, we need to update the effectiveTickets on all
			// the lotteryQueues we own and are waiting on
			updateAllEffectiveTickets();
		}

		/*
		 * Removes a lotteryQueue from the waitResourceList to the
		 */
		void waitForAccess(LotteryQueue lotteryQueue)
		{
			release(lotteryQueue);

			// If we're not already in the lotteryQueue's waitingResources
			if (!lotteryQueue.waitingResources.contains(this))
			{
				// Add the lotteryQueue to the waitingResourceList
				waitResourceList.add(lotteryQueue);

				// Add ourselves into the waitingResources of the lotteryQueue
				lotteryQueue.waitingResources.add(this);

				// If the resourceHolder of lotteryQueue is not null
				if (lotteryQueue.transferTickets && lotteryQueue.resourceHolder != null)
				{
					// Update the effectiveTickets on all the lotteryQueues we own and are waiting on
					lotteryQueue.resourceHolder.updateAllEffectiveTickets();
				}
				// Otherwise, we just update the effectiveTickets belonging to the lotteryQueue
				// This is only done when the lotteryQueue is new
				else
				{
					lotteryQueue.updateEffectiveTickets();
				}
			}
		}

		/*
		 * Acquires a LotteryQueue if the resourceHolder isn't already ourselves
		 * Calls the release() function on the oldOwner of the lotteryQueue
		 * Remove ourselves from the waitingResources of lotteryQueue
		 * Set ourselves as the new owner of lotteryQueue
		 * Add the lotteryQueue to our resourceList
		 * Remove the lotteryQueue from our waitingResourceList
		 */
		void acquire(LotteryQueue lotteryQueue)
		{
			// If we're already the resourceHolder, do nothing
			if (lotteryQueue.resourceHolder != this)
			{
				// If the resourceHolder is not null,
				if (lotteryQueue.resourceHolder != null)
				{
					// We release this lotteryQueue from its resourceList
					lotteryQueue.resourceHolder.release(lotteryQueue);
				}

				// Remove ourselves from the waitingResources of the lotteryQueue
				lotteryQueue.removeFromWaiting(this);

				// Set ourselves as the new resourceHolder of the lotteryQueue
				lotteryQueue.resourceHolder = this;

				// Add this lotteryQueue to our resourceList
				resourceList.add(lotteryQueue);

				// Remove this lotteryQueue from our waitResourceList
				waitResourceList.remove(lotteryQueue);

				// Update the effectiveTickets on all the LotteryQueues we own and are waiting on
				updateAllEffectiveTickets();
			}
		}

		/*
		 * Releases a resourceHolder (ourselves) from a lotteryQueue and updates tickets
		 */
		private void release(LotteryQueue lotteryQueue)
		{
			// If we're the resourceHolder of the lotteryQueue
			if (lotteryQueue.resourceHolder == this)
			{
				// Remove the lotteryQueue from our resourceList
				resourceList.remove(lotteryQueue);

				// Set the resourceHolder to be null
				lotteryQueue.resourceHolder = null;

				// Update the effectiveTickets on all the lotteryQueues we own and are waiting on
				updateAllEffectiveTickets();
			}
		}
	}
}