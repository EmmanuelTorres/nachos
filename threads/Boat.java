package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	static BoatGrader bg;
	private static Communicator communicator;
	private static Lock boatLock;
	private static String boatLocation;
	static private int totalAdults;
	static private int totalChildren;
	static private int adultsOnOahu;
	static private int childrenOnOahu;
	static private int adultsOnMolokai;
	static private int childrenOnMolokai;

	public static void selfTest()
	{
		// This initializes the BoatGrader that we need to make calls to
		// in order to get graded
		BoatGrader b = new BoatGrader();

		// NOTE* All cases are solved for mathematical induction tests
//		System.out.println("\n ***Testing Mathematical Induction Cases***");
//		begin(0, 2, b);
//		begin(1, 2, b);
//		begin(0, 3, b);
//		begin(2, 2, b);
//		begin(2, 3, b);
//		begin(3, 3, b);

		// NOTE* All cases are solved for stress tests
//		System.out.println("\n ***Testing Stress Case***");
//		begin(0, 47, b);
//		begin(23, 3, b);
		begin(13, 92, b);
	}

	public static void begin( int adults, int children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		communicator = new Communicator();
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

		// While the communicator sees that there are still threads on Oahu
		while (communicator.listen() != (totalAdults + totalChildren))
		{
			if (communicator.listen() == (totalAdults + totalChildren))
			{
				break;
			}
		}
	}

	static void AdultItinerary()
	{
		while (true)
		{
			// If the solution has been solved, we can tell the communicator that we have
			// everyone on Molokai
			if (totalAdults == adultsOnMolokai && totalChildren == childrenOnMolokai)
			{
				communicator.speak(totalAdults + totalChildren);

				break;
			}

			// If the boat is on Oahu
			if (boatLocation.equals("Oahu"))
			{
				// If there is one adult on Oahu and at least one child on Oahu
				if (adultsOnOahu >= 1 && (totalChildren != childrenOnOahu))
				{
					// Make sure nobody else can row besides us
					boatLock.acquire();

					// Row the adult to Molokai
					bg.AdultRowToMolokai();

					// Change the values of adults to signify the change
					adultsOnOahu -= 1;
					adultsOnMolokai += 1;

					// The boat is now on Molokai
					boatLocation = "Molokai";

					// If there are children remaining on Oahu, send a child to pick them up
					// This should ALWAYS be the case
					if (totalChildren != childrenOnMolokai)
					{
						// Send one child to pick up the rest of the children at Oahu
						bg.ChildRowToOahu();

						// Change the values to signify this change
						childrenOnOahu += 1;
						childrenOnMolokai -= 1;

						// The boat moves back to Oahu
						boatLocation = "Oahu";
					}

					// Release the lock we held
					boatLock.release();
				}
			}

			// Prevent busy waiting by yielding this thread while its conditions are not met
			KThread.yield();
		}
	}

	static void ChildItinerary()
	{
		// bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.

		while (true)
		{
			// If the solution has been solved, we can tell the communicator that we have
			// everyone on Molokai
			if (totalAdults == adultsOnMolokai && totalChildren == childrenOnMolokai)
			{
				communicator.speak(totalAdults + totalChildren);

				break;
			}

			// If the boat is on Oahu
			if (boatLocation.equals("Oahu"))
			{
				// If the amount of adults on Oahu is 0, we only have to commute children
				// to Molokai
				if (adultsOnOahu == 0)
				{
					// While there are childrenOnOahu
					while (childrenOnOahu > 0)
					{
						// If we are the only child there, we row back alone to Molokai
						if (childrenOnOahu == 1)
						{
							bg.ChildRowToMolokai();

							childrenOnOahu -= 1;
							childrenOnMolokai += 1;

							boatLocation = "Molokai";
						}
						// If there is more than one child there, we row two people back to Molokai
						// and we send one child to Oahu to pick up his friend
						else
						{
							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();

							childrenOnOahu -= 2;
							childrenOnMolokai += 2;

							boatLocation = "Molokai";

							bg.ChildRowToOahu();

							childrenOnOahu += 1;
							childrenOnMolokai -= 1;

							boatLocation = "Oahu";
						}
					}

					break;
				}

				// If there are at least 2 children on Oahu
				if (childrenOnOahu >= 2)
				{
					boatLock.acquire();

					// Send a boat with two children to Molokai
					bg.ChildRowToMolokai();
					bg.ChildRideToMolokai();

					// Adjust the values to show the change
					childrenOnOahu -= 2;
					childrenOnMolokai += 2;

					boatLocation = "Molokai";

					// If there is at least one adult back at Oahu, we send one child to pick them up
					if (totalAdults != adultsOnMolokai)
					{
						bg.ChildRowToOahu();

						childrenOnOahu += 1;
						childrenOnMolokai -= 1;

						boatLocation = "Oahu";
					}

					boatLock.release();
				}
			}
			else if (boatLocation.equals("Molokai"))
			{
				boatLock.acquire();

				// If all the adults are on Molokai
				if (totalAdults > 0)
				{
					if (totalAdults == adultsOnMolokai)
					{
						// We put children from Oahu to Molokai
						while (totalChildren < childrenOnMolokai)
						{
							bg.ChildRowToOahu();

							childrenOnMolokai -= 1;
							childrenOnOahu += 1;

							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();

							childrenOnMolokai += 2;
							childrenOnOahu -= 2;
						}

						// Let the communicator know that everyone is on Molokai
						communicator.speak(totalAdults + totalChildren);
					}
				}

				// If there is at least one child back at Oahu, we send one child to pick them up
				if (totalChildren != childrenOnMolokai)
				{
					bg.ChildRowToOahu();

					childrenOnOahu += 1;
					childrenOnMolokai -= 1;

					bg.ChildRowToMolokai();
					bg.ChildRideToMolokai();

					childrenOnOahu -= 2;
					childrenOnMolokai += 2;
				}

				boatLock.release();
			}

			// If conditions are not met, yield the thread
			KThread.yield();
		}
	}

	static void SampleItinerary()
	{
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
//		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}