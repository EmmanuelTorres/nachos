package nachos.threads;
import nachos.ag.BoatGrader;

public class Boat
{
	static BoatGrader bg;
	private static Communicator communicator;
	private static Lock boatLock;

	
	 static private int children;//	total number of children
	 static private int adult;//	total number of adults
	 static private int childrenOnMolokai;//	children on this island
	 static private int childrenOnOahu;//	 children on this island
	 static private int adultsOnMolokai;//	 adults on this island
	 static private int adultsOnOahu;//	adults on this island
	 static private boolean boatAtOahu;// boatlocation
	
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

	public static void begin( int Adults, int Children, BoatGrader b )
	{
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		communicator = new Communicator();
		boatLock = new Lock();
		
		boatAtOahu=false;
		adult=Adults;
		children=Children;
		
		adultsOnOahu=Adults;
		childrenOnOahu=Children;
		adultsOnMolokai=0;;
		childrenOnMolokai=0;
		// Initialize all adult threads
		Runnable adultRunnable = new Runnable()
		{
			public void run()
			{
				AdultItinerary();
			}
		};
		for (int i = 0; i < Adults; i++)
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
		for (int i = 0; i < Children; i++)
		{
			KThread childThread = new KThread(childRunnable);
			childThread.setName("Child " + i);
			childThread.fork();
		}

		// While the communicator sees that there are still threads on Oahu
	
		while (communicator.listen() != (Adults + Children)){
			if (communicator.listen() == (Adults + Children))
				break;
		}
	}

	static void AdultItinerary()//	itinerary for adults
	{
		while ( (adultsOnOahu!=0)){// #and childrenOnOahu!=0) and (adultsOnMolokai+childrenOnMolokai)!=(children+adult) ):

			if(!boatAtOahu) //if the boat is on molokai
				KThread.yield();// yield because you cannot do anything since you never row back
			else if (childrenOnOahu>=2 || childrenOnOahu==0){// yield because you only row when there is only 1 kid on the island 
				KThread.yield();
			}
			else{//there is one kid on oahu so one adult must row over then
				boatLock.acquire();
				bg.AdultRowToMolokai();
				adultsOnMolokai+=1;//we just rowed over an adult from Oahu	
				adultsOnOahu-=1;//we just rowed over an adult to molokai
				boatAtOahu=false;//boat is at molokai
				communicator.speak(adultsOnMolokai+childrenOnMolokai);
				boatLock.release();
			}
		}	
		communicator.speak(adultsOnMolokai+childrenOnMolokai);// incase if the loop terminates and it needs to speak the location to terminate main loop 	
	}

	static void ChildItinerary()
	{
		// bg.initializeChild(); //Required for autograder interface. Must be the first thing called.
		//DO NOT PUT ANYTHING ABOVE THIS LINE.
	
	
		while((adultsOnMolokai+childrenOnMolokai)!=(children+adult)){//terminal state based on molokai because a child is always going to be last to row to molokai
		
			if(!boatAtOahu && adultsOnMolokai>0 ){//if the boat is at molokai because an adult rowed over it means there is atleast 1 kid on oahu so we need to pick him up 
				boatLock.acquire();
				bg.ChildRowToMolokai();//row a child over to oahu
				childrenOnMolokai-=1;
				childrenOnOahu+=1;
				boatAtOahu=true;
				//communicator.speak(adultsOnMolokai+childrenOnMolokai);	This is unnecessary 
				boatLock.release();
			}
			while(boatAtOahu && childrenOnOahu>=2 ){//if there are at least 2 kids on oahu or youre at molokai and there is a kid left on oahu, this will row over every kid but 1
				//youre at oahu and theres at least 2 kids so row over 2
				boatLock.acquire();
				bg.ChildRowToMolokai();//row a kid to molokai
				bg.ChildRideToMolokai();//ride a kid
				childrenOnOahu-=2;
				childrenOnMolokai+=2;
				boatAtOahu=false;
				communicator.speak(adultsOnMolokai+childrenOnMolokai);//speak the number of people on molokai
				
				if((childrenOnMolokai+adultsOnMolokai)==(adult+children)){// if the program needs to end
					communicator.speak(adultsOnMolokai+childrenOnMolokai);//speak the number of people on molokai
					boatLock.release();
					break;
				}
				bg.ChildRowToOahu();// the program did not end which means there are people (adults) still on oahu so send 1 kid back to oahu so an adult can row over
				childrenOnMolokai-=1;
				childrenOnOahu+=1;
				boatAtOahu=true;
				communicator.speak(adultsOnMolokai+childrenOnMolokai);
				boatLock.release();
			}
			if(boatAtOahu && childrenOnOahu ==1)// if the boat is at oahu and there is 1 kid then do nothing and let the adults do their thing
				KThread.yield();
		}
		communicator.speak(adultsOnMolokai+childrenOnMolokai); // incase if the loop terminates and it needs to speak the location to terminate main loop 	
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
