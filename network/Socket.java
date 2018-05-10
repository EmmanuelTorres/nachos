package nachos.network;

import nachos.threads.*;
import java.util.Vector;
import java.util.concurrent.ArrayBlockingQueue;

public class Socket
{
	public enum State {
		CLOSED, SYN_SENT, SYN_RCVD, ESTABLISHED, STP_RCVD, STP_SENT, CLOSING
	}

	public State state;

	private int hostAddress;
	private int hostPort;
	private int clientAddress;
	private int clientPort;

	public Lock needAcceptLock;
	public Condition needAccept;

	private static final int WINDOW_SIZE = 16;
	public Semaphore packetCredit; // need to enforce <= 16

	public Vector<MailMessage> sendBuffer;
	public ArrayBlockingQueue<MailMessage> receiveBuffer;
	public int seqnoIndex;

	public Socket(int hostAddress, int hostPort, int clientAddress, int clientPort)
	{
		state = Socket.State.CLOSED;

		this.hostAddress = hostAddress;
		this.hostPort = hostPort;
		this.clientAddress = clientAddress;
		this.clientPort = clientPort;

		needAcceptLock = new Lock();
		needAccept = new Condition(needAcceptLock);

		packetCredit = new Semaphore(WINDOW_SIZE);

		sendBuffer = new Vector<MailMessage>();
		receiveBuffer = new ArrayBlockingQueue(WINDOW_SIZE);

		seqnoIndex = 0;
	}
	public int getHostAddress() {
		return hostAddress;
	}
	public int getHostPort() {
		return hostPort;
	}
	public int getClientAddress() {
		return clientAddress;
	}
	public int getClientPort() {
		return clientPort;
	}
}
