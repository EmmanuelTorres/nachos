package nachos.network;

import nachos.machine.*;
import nachos.security.Privilege;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;
import java.util.Arrays;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends UserProcess {
	private static final int CHAT_PORT = 15;
	private static final int SOCKET_SIZE = 16;

	private Socket[] socketDescriptor = new Socket[SOCKET_SIZE];

	private PostOffice postOffice;

    /**
     * Allocate a new process.
     */
    public NetProcess() {
		super();
	    postOffice = new PostOffice();
    }

	/**
	 * Checks to see if a port is within the valid bounds according
	 * to the NachOS design constraints
	 * @param port The port we're checking against
	 * @return true if the port is within 0 and 126
	 */
	private boolean withinPortBounds(int port) {
    	return port >= 0 && port <= 126;
    }

	/**
	 * Checks to see if the socket is available
	 * @param socket The socket we want to check and see if it is open
	 * @return true if the socket is open
	 */
	private boolean withinSocketBounds(int socket) {
    	return socket > 0 && socket < SOCKET_SIZE;
    }

	/**
	 * A helper function to get the next available socket descriptor
	 * @return The index for an available socket descriptor on success, -1 on error
	 */
	private int getAvailableSocketDescriptor() {
    	for (int i = 0; i < SOCKET_SIZE; i++) {
	    	if (socketDescriptor[i] == null) {
		    	return i;
		    }
	    }

	    return -1;
    }

    private static final int
	syscallConnect = 11,
	syscallAccept = 12,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8;

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>11</td><td><tt>int  connect(int host, int port);</tt></td></tr>
     * <tr><td>12</td><td><tt>int  accept(int port);</tt></td></tr>
     * </table>
     *
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
			case syscallConnect:
				return handleConnect(a0, a1);
			case syscallAccept:
				return handleAccept(a0);
			case syscallRead:
				return handleRead(a0, a1, a2);
			case syscallWrite:
				return handleWrite(a0, a1, a2);
			case syscallClose:
				return handleClose(a0);
			default:
			    return super.handleSyscall(syscall, a0, a1, a2, a3);
		}
    }

	/**
	 *
	 * @param socketfd the socket file descriptor
	 * @param buffer the buffer to copy stuff from
	 * @param port amount of stuff to write
	 * @return The amount of bytes written, or -1 on error
	 */
	private int handleWrite(int socketfd, int buffer, int amount)
	{
		// Boundary check to see if the provided file descriptor is valid
		// Return error if the fileDescriptor is invalid
		if(isInvalidSocketDescriptorIndex(socketfd))
			return -1;

		// Get the socket defined by the socket file descriptor
		Socket socket = socketDescriptor[socketfd];
        byte[] temp = new byte[amount];

        // Get the number of bytes read
        int bytesRead = readVirtualMemory(buffer, temp, 0, amount);

		// Writes from the buffer into the send buffer
		for(int i = 0; i < amount;)
		{
			// Attempt to maximize the size of each MailMessage
			int end = i + MailMessage.maxContentsLength;

			// If it comes out too large, scale it to the size specified
			if(end > amount) {
				end = amount;
			}

			// Creates a copy of specific ranges from temp
			byte[] currentContents = Arrays.copyOfRange(temp, i, end);

			// Prep contents for transmission by adding into the socket's send buffer
			socket.sendBuffer.add(new MailMessage(socket, ++socket.seqnoIndex, currentContents));

			// jump to the start of the next block to be written
			i = ++end;
		}

		return amount;
	}

	/**
	 *
	 * @param socketfd the socket file descriptor
	 * @param buffer the buffer to copy stuff into
	 * @param port amount of stuff to read
	 * @return The amount of bytes read, 0 on no bytes available, or -1 on error
	 */
	private int handleRead(int socketfd, int buffer, int amount)
	{
		// Boundary check to see if the provided file descriptor is valid
		// Return error if the fileDescriptor is invalid
		if(isInvalidSocketDescriptorIndex(socketfd))
			return -1;

		int bytesWritten = 0;

		// Get the socket defined by the socket file descriptor
		Socket socket = socketDescriptor[socketfd];

		//	Iterate through the buffer for the amount defined by the seqnoIndex
		for(int i = 0; i < socket.seqnoIndex; i++)
		{
			//	Pop the message from the buffer and write it to VM
			byte[] payload = socket.receiveBuffer.poll().contents;

			// Add the total bytes written from the writeVM function
			bytesWritten += writeVirtualMemory(buffer, payload, 0, amount);
		}

		return bytesWritten;
	}

	/**
	 * Handles the tear-down between the two connections. This will first receive
	 * the FIN acknowledgement from the server and send any data in the buffer to
	 * the client and then send the client our FIN acknowledgement
	 * @param socket
	 * @return
	 */
	private int handleClose(int s) {
		if(isInvalidSocketDescriptorIndex(s))
			return -1;

		// All bounds should have been checked by this point, so no need
		//  to redundantly check again
		Socket socket = socketDescriptor[s];
		postOffice.send(new MailMessage(socket, MailMessage.Type.STP));
		socket.state = Socket.State.CLOSING;

		// idk what to return here at the moment
		return 0;
    }

	/**
	 * Attempt to initiate a new connection to the specified port on the specified
	 * remote host, and return a new file descriptor referring to the connection.
	 * connect() does not give up if the remote host does not respond immediately.
	 * @return the new file descriptor, or -1 if an error occurred.
	 */
    private int handleConnect(int host, int port) {
	    // The first thing we do is check if there is availability for a new Socket
	    // object that will be put on a socketDescriptor
	    int openSocketDescriptor = getAvailableSocketDescriptor();

	    // If there isn't any open file descriptors or the port is not within bounds, we return -1
	    if (openSocketDescriptor == -1 || !withinPortBounds(port)) {
	    	return openSocketDescriptor;
	    }

	    // The address of our machine
	    int localAddress = Machine.networkLink().getLinkAddress();

	    // Create a socket to assign to socketDescriptor
	    // A socket just holds variables so we know who we're talking to
	    Socket socket = new Socket(localAddress, port, host, port);

	    // Assign the openSocketDescriptor a Socket object that will listen for
	    // calls from this specific program that handled the connection
	    socketDescriptor[openSocketDescriptor] = socket;

	    // Send the SYN packet
	    postOffice.send(new MailMessage(socket, MailMessage.Type.SYN));
	    socket.state = Socket.State.SYN_SENT;

	    // Sleep until SYN/ACK received
	    socket.needAcceptLock.acquire();
	    socket.needAccept.sleep();
	    socket.needAcceptLock.release();

	    // Return a new file descriptor referring to the connection
	    return openSocketDescriptor;
    }

	/**
	 * Attempt to accept a single connection on the specified local port and return
	 * a file descriptor referring to the connection.
	 *
	 * If any connection requests are pending on the port, one request is dequeued
	 * and an acknowledgement is sent to the remote host (so that its connect()
	 * call can return). Since the remote host will never cancel a connection
	 * request, there is no need for accept() to wait for the remote host to
	 * confirm the connection (i.e. a 2-way handshake is sufficient; TCP's 3-way
	 * handshake is unnecessary).
	 *
	 * If no connection requests are pending, returns -1 immediately.
	 *
	 * In either case, accept() returns without waiting for a remote host.
	 *
	 * Returns a new file descriptor referring to the connection, or -1 if an error
	 * occurred.
	 */
	private int handleAccept(int port) {
		// The first thing we do is check if there is availability for a new Socket
		// object that will be put on a socketDescriptor
		int openSocketDescriptor = getAvailableSocketDescriptor();

		// If there isn't any open file descriptors or the port is not within bounds, we return -1
		if (openSocketDescriptor == -1 || !withinPortBounds(port)) {
			return openSocketDescriptor;
		}

		MailMessage mail = postOffice.receive(port);
		if(mail.isSyn()) {
			Socket mailSocket = mail.getSocket();
			if(mailSocket.state == Socket.State.SYN_SENT) {
				Socket socket = new Socket(mailSocket.getClientAddress(), mailSocket.getClientPort(), mailSocket.getHostAddress(), mailSocket.getHostPort());

				// Assign the openSocketDescriptor a Socket object that will listen for
				// calls from this specific program that handled the connection
				socketDescriptor[openSocketDescriptor] = socket;

				// Send the SYN/ACK packet
				postOffice.send(new MailMessage(socket, MailMessage.Type.SYNACK));

				// Wake sleeping connect() thread
				mailSocket.needAcceptLock.acquire();
				mailSocket.needAccept.wake();
				mailSocket.needAcceptLock.release();

				// Update socket states
				mailSocket.state = Socket.State.ESTABLISHED;
				socket.state = Socket.State.ESTABLISHED;

				// Return a new file descriptor referring to the connection
				return openSocketDescriptor;
			}
		}
		return -1;
	}

    public boolean isInvalidSocketDescriptorIndex(int socketfd) {
            return socketfd < 0 || socketfd >= 16 || socketDescriptor[socketfd] == null;
    }
}
