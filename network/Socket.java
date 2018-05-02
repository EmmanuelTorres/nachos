package nachos.network;

public class Socket
{
	private int hostAddress;
	private int hostPort;
	private int clientAddress;
	private int clientPort;

	public Socket(int hostAddress, int hostPort, int clientAddress, int clientPort)
	{
		this.hostAddress = hostAddress;
		this.hostPort = hostPort;
		this.clientAddress = clientAddress;
		this.clientPort = clientPort;
	}
}
