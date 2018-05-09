package nachos.network;

import nachos.threads.*;

public class Socket
{
	private int hostAddress;
	private int hostPort;
	private int clientAddress;
	private int clientPort;
	static private int WINDOW_SIZE = 16;
	public Semaphore packetCredit;

	public Socket(int hostAddress, int hostPort, int clientAddress, int clientPort)
	{
		this.hostAddress = hostAddress;
		this.hostPort = hostPort;
		this.clientAddress = clientAddress;
		this.clientPort = clientPort;
		packetCredit = new Semaphore(WINDOW_SIZE);
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
