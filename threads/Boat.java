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

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(1, 2, b);

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
		/*
		 * Adult Cases
		 * Oahu
		 *      1) There is at least one child on Oahu to row the boat back
		 *          We row to Molokai. Expected behavior: A child on Molokai will
		 *          row back with an additional child and re-supply Oahu.
		 * Molokai
		 *      1) Sleep
		 */

		while (true)
		{
			System.out.println("Adult Itinerary");

			if (boatLocation.equals("Oahu"))
			{
				if (childrenOnOahu <= 1)
				{
					boatLock.acquire();

					bg.AdultRowToMolokai();

					adultsOnOahu -= 1;
					adultsOnMolokai += 1;

					boatLocation = "Molokai";

					if (totalChildren == childrenOnMolokai)
					{
						bg.ChildRowToOahu();

						childrenOnOahu += 1;
						childrenOnMolokai -= 1;
					}

					boatLock.release();

					break;
				}
			}
			else if (boatLocation.equals("Molokai"))
			{
				if (totalAdults == adultsOnMolokai && totalChildren == childrenOnMolokai)
				{
					communicator.speak(3);
				}
			}

			KThread.yield();
		}

		System.out.println("AOO: " + adultsOnOahu + ", COO: " + childrenOnOahu);
		System.out.println("AOM: " + adultsOnMolokai + ", COM: " + childrenOnMolokai);
	}

	static void ChildItinerary()
	{
		// bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.

		System.out.println("Child Itinerary");

		while (true)
		{
			if (boatLocation.equals("Oahu"))
			{
				// If there are
				if (childrenOnOahu >= 2)
				{
					boatLock.acquire();

					bg.ChildRowToMolokai();
					bg.ChildRideToMolokai();

					childrenOnOahu -= 2;
					childrenOnMolokai += 2;

					boatLocation = "Molokai";

					bg.ChildRowToOahu();
					childrenOnOahu += 1;
					childrenOnMolokai -= 1;

					boatLocation = "Oahu";

					boatLock.release();

					break;
				}
			}
			else if (boatLocation.equals("Molokai"))
			{
				if (totalAdults == adultsOnMolokai)
				{
					if (totalChildren == childrenOnMolokai)
					{
						communicator.speak(3);

						break;
					}
					else
					{
						boatLock.acquire();

						bg.ChildRowToOahu();

						childrenOnMolokai -= 1;
						childrenOnOahu += 1;

						boatLocation = "Oahu";

						boatLock.release();

						break;
					}
				}
			}

			KThread.yield();
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