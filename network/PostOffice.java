package nachos.network;

import nachos.machine.*;
import nachos.threads.*;

/**
 * A collection of message queues, one for each local port. A
 * <tt>PostOffice</tt> interacts directly with the network hardware. Because
 * of the network hardware, we are guaranteed that messages will never be
 * corrupted, but they might get lost.
 *
 * <p>
 * The post office uses a "postal worker" thread to wait for messages to arrive
 * from the network and to place them in the appropriate queues. This cannot
 * be done in the receive interrupt handler because each queue (implemented
 * with a <tt>SynchList</tt>) is protected by a lock.
 */
public class PostOffice {
    /**
     * Allocate a new post office, using an array of <tt>SynchList</tt>s.
     * Register the interrupt handlers with the network hardware and start the
     * "postal worker" thread.
     */
    public PostOffice() {
	messageReceived = new Semaphore(0);
	messageSent = new Semaphore(0);
	sendLock = new Lock();

	queues = new SynchList[MailMessage.portLimit];
	for (int i=0; i<queues.length; i++)
	    queues[i] = new SynchList();

	Runnable receiveHandler = new Runnable() {
	    public void run() { receiveInterrupt(); }
	};
	Runnable sendHandler = new Runnable() {
	    public void run() { sendInterrupt(); }
	};
	Machine.networkLink().setInterruptHandlers(receiveHandler,
						   sendHandler);

	KThread t = new KThread(new Runnable() {
		public void run() { postalDelivery(); }
	    });

	t.fork();
    }

    /**
     * Retrieve a message on the specified port, waiting if necessary.
     *
     * @param	port	the port on which to wait for a message.
     *
     * @return	the message received.
     */
    public MailMessage receive(int port) {
	Lib.assertTrue(port >= 0 && port < queues.length);

	Lib.debug(dbgNet, "waiting for mail on port " + port);

	MailMessage mail = (MailMessage) queues[port].removeFirst();
	if(mail.isAck()) {
		mail.socket.packetCredit.V();
	}

	if (Lib.test(dbgNet))
	    System.out.println("got mail on port " + port + ": " + mail);

	return mail;
    }

    /**
     * Wait for incoming messages, and then put them in the correct mailbox.
     */
    private void postalDelivery() {
	while (true) {
	    messageReceived.P();

	    Packet p = Machine.networkLink().receive();

	    MailMessage mail;

	    try {
		mail = new MailMessage(p);
	    }
	    catch (MalformedPacketException e) {
		continue;
	    }

	    if (Lib.test(dbgNet))
		System.out.println("delivering mail to port " + mail.dstPort
				   + ": " + mail);

	    // atomically add message to the mailbox and wake a waiting thread
	    queues[mail.dstPort].add(mail);
	}
    }

    /**
     * Called when a packet has arrived and can be dequeued from the network
     * link.
     */
    private void receiveInterrupt() {
	messageReceived.V();
    }

    /**
     * Send a message to a mailbox on a remote machine.
     */
    public void send(MailMessage mail) {
	if (Lib.test(dbgNet))
	    System.out.println("sending mail: " + mail);

	sendLock.acquire();

	Machine.networkLink().send(mail.packet);
	messageSent.P();

	sendLock.release();
    }

    public void sendData(Socket socket, int seqno, byte[] contents) {
	if(socket.state != Socket.State.ESTABLISHED)
		return;
	try {
		MailMessage mail = new MailMessage(socket, false, false, false, false, seqno, contents);

		if (Lib.test(dbgNet))
		    System.out.println("sending data packet: " + seqno + contents);

		sendLock.acquire();

		socket.packetCredit.P();
		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    public void sendFin(Socket socket) {
	try {
		MailMessage mail = new MailMessage(socket, true, false, false, false, 0, new byte[0]);
		if (Lib.test(dbgNet))
		    System.out.println("sending fin packet");

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    public void sendStp(Socket socket) {
	try {
		MailMessage mail = new MailMessage(socket, false, true, false, false, 0, new byte[0]);
		if (Lib.test(dbgNet))
		    System.out.println("sending stp packet");

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    public void sendAck(Socket socket) {
	try {
		MailMessage mail = new MailMessage(socket, false, false, true, false, 0, new byte[0]);
		if (Lib.test(dbgNet))
		    System.out.println("sending ack packet");

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    public void sendSyn(Socket socket) {
	try {
		MailMessage mail = new MailMessage(socket, false, false, false, true, 0, new byte[0]);
		if (Lib.test(dbgNet))
		    System.out.println("sending syn packet");

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    public void sendSynAck(Socket socket) {
	try {
		MailMessage mail = new MailMessage(socket, false, false, true, true, 0, new byte[0]);
		if (Lib.test(dbgNet))
		    System.out.println("sending synack packet");

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    public void sendFinAck(Socket socket) {
	try {
		MailMessage mail = new MailMessage(socket, true, false, true, false, 0, new byte[0]);
		if (Lib.test(dbgNet))
		    System.out.println("sending finack packet");

		sendLock.acquire();

		Machine.networkLink().send(mail.packet);

		messageSent.P();

		sendLock.release();
	}
	catch (MalformedPacketException e) {
 		    e.printStackTrace();
	}
    }

    /**
     * Called when a packet has been sent and another can be queued to the
     * network link. Note that this is called even if the previous packet was
     * dropped.
     */
    private void sendInterrupt() {
	messageSent.V();
    }

    private SynchList[] queues;
    private Semaphore messageReceived;	// V'd when a message can be dequeued
    private Semaphore messageSent;	// V'd when a message can be queued
    private Lock sendLock;

    private static final char dbgNet = 'n';
}
