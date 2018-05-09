package nachos.network;

import nachos.threads.*;

public class Socket
{
	private int hostAddress;
	private int hostPort;
	private int clientAddress;
	private int clientPort;
	static private int WINDOW_SIZE = 16;
	private int packetCredits;
	private Lock packetCreditLock;
	public Condition needPacketCredit;

	public Socket(int hostAddress, int hostPort, int clientAddress, int clientPort)
	{
		this.hostAddress = hostAddress;
		this.hostPort = hostPort;
		this.clientAddress = clientAddress;
		this.clientPort = clientPort;
		packetCredits = WINDOW_SIZE;
		packetCreditLock = new Lock();
		needPacketCredit = new Condition(packetCreditLock);
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
	public boolean hasPacketCredit() {
		return packetCredits > 0;
	}
	public synchronized void incPacketCredit() {
		if(packetCredits < WINDOW_SIZE) {
			packetCredits++;
			needPacketCredit.wake();
		}
	}
	public synchronized void decPacketCredit() {
		if(packetCredits > 0)
			packetCredits--;
	}

}
