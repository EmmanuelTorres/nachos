package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	static BoatGrader bg;
	private static Lock boatLock;
	private static String boatLocation;
	static private int totalAdults;
	static private int totalChildren;
	static private int adultsOnOahu;
	static private int childrenOnOahu;

	public static void selfTest()
	{
		// This initializes the BoatGrader that we need to make calls to
		// in order to get graded
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(0, 2, b);

		// Put more test cases here in this format:
		// System.out.println("\n ***Testing Boats with 5 children, 8 adults***");
		// begin(8, 5, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		// All variables need to be static since the function calls are static

		boatLock = new Lock();
		boatLocation = "Oahu";

		// Initialize the number of total adults and children
		totalAdults = adults;
		totalChildren = children;

		adultsOnOahu = adults;
		childrenOnOahu = children;

		// Initialize all adult threads
		Runnable adultRunnable = new Runnable()
		{
			public void run()
			{
				AdultItinerary();
			}
		};
		for (int i = 0; i < totalAdults; i++)
		{
			KThread adultThread = new KThread(adultRunnable);
			adultThread.setName("Adult " + i);
			adultThread.fork();
		}

		// Initialize all child threads
		Runnable childRunnable = new Runnable()
		{
			public void run()
			{
				ChildItinerary();
			}
		};
		for (int i = 0; i < totalChildren; i++)
		{
			KThread childThread = new KThread(childRunnable);
			childThread.setName("Child " + i);
			childThread.fork();
		}
	}

	static void AdultItinerary()
	{
		// bg.initializeAdult(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.

		/* This is where you should put your solutions. Make calls
		   to the BoatGrader to show that it is synchronized. For
		   example:
		       bg.AdultRowToMolokai();
		   indicates that an adult has rowed the boat across to Molokai
		*/

		/*
		 * Adult Cases
		 * Oahu
		 *      1) There is at least one child on Oahu to row the boat back
		 *          We row to Molokai. Expected behavior: A child on Molokai will
		 *          row back with an additional child and re-supply Oahu.
		 * Molokai
		 *      1) Sleep
		 */

		while (adultsOnOahu >= 0 && childrenOnOahu >= 0)
		{
			if (boatLocation.equals("Oahu"))
			{
				// If there is at least one child on Molokai to row the boat back
				if (adultsOnOahu == 1 || childrenOnOahu != totalChildren)
				{
					// We acquire the lock so that no other thread can ride the boat
					boatLock.acquire();

					// We row ourselves to Molokai
					bg.AdultRowToMolokai();

					boatLocation = "Molokai";

					adultsOnOahu -= 1;

					// We release the lock
					boatLock.release();
				}
			}
			else if (boatLocation.equals("Molokai"))
			{
				// We sleep the thread since it no longer has to do anything
				// TODO: Figure out a way to sleep this thread
			}
		}
	}

	static void ChildItinerary()
	{
		// bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.

	    /*
	     * Oahu
	     *      1) The number of total adults is the same as the number of adults on Molokai
	     *          In this case, we sleep because the problem has been solved
	     *      2) There aren't enough children on Molokai, so we sent more
	     * Molokai
	     *      1) End condition
	     *      2) There are no adults on Molokai, so we just row children over repeatedly
	     */

	    while (adultsOnOahu >= 0 && childrenOnOahu>= 0)
	    {
		    // If the boat is on Oahu
		    if (boatLocation.equals("Oahu"))
		    {
			    // If all the adults are on Molokai, we are done with the program
			    if ((totalAdults - adultsOnOahu) == 0)
			    {
				    boatLock.acquire();

				    bg.ChildRowToMolokai();

				    childrenOnOahu -= 1;

				    // Since the boat is on Oahu, we can assume there have to be children on Oahu
				    if (childrenOnOahu >= 2)
				    {
					    bg.ChildRideToMolokai();

					    childrenOnOahu -= 1;
				    }

				    boatLock.release();
				    // Sleep
				    // TODO: Figure out a way to sleep this thread
			    }
			    // If the number of children on Molokai is too low, we focus on getting more
			    // children to Molokai
			    else if ((totalChildren - childrenOnOahu) < 2)
			    {
				    // TODO: Fix this to make one child row back
				    // Row more children to Molokai
				    for (int i = childrenOnOahu; i < (totalChildren - childrenOnOahu); i++)
				    {
					    if (i % 2 == 0)
					    {
						    boatLock.acquire();

						    bg.ChildRowToMolokai();
						    bg.ChildRideToMolokai();

						    childrenOnOahu -= 2;

						    boatLocation = "Molokai";

						    boatLock.release();
					    }
				    }
			    }
		    }
		    // If the boat is on Molokai
		    else if (boatLocation.equals("Molokai"))
		    {
			    // If everyone is on Molokai
			    if (childrenOnOahu == 0 && adultsOnOahu == 0)
			    {
				    // Put this child thread to sleep because the boat problem is done
				    // TODO: Find a way to put this thread to sleep
			    }
			    // If the adults on Oahu is 0, we just want to row over the remaining
			    // children from Oahu onto Molokai in order to solve the boat problem
			    else if (adultsOnOahu == 0 && childrenOnOahu > 0)
			    {
				    boatLock.acquire();

				    if ((totalChildren - childrenOnOahu) > 2)
				    {

					    bg.ChildRowToOahu();
					    bg.ChildRideToOahu();

					    childrenOnOahu += 2;
				    }
				    else
				    {
					    // Row more children to Molokai
					    bg.ChildRowToOahu();
					    childrenOnOahu += 1;
				    }

				    boatLocation = "Oahu";

				    boatLock.release();
			    }
		    }
	    }
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}