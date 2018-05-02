package nachos.network;

import nachos.machine.*;
import nachos.security.Privilege;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.IOException;
import java.net.DatagramPacket;
import java.util.HashMap;

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
	syscallAccept = 12;
    
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
			default:
			    return super.handleSyscall(syscall, a0, a1, a2, a3);
		}
    }

	/**
	 *
	 * @param socket
	 * @param buffer
	 * @param port
	 * @return The amount of bytes written, or -1 on error
	 */
	private int handleWrite(int socket, int buffer, int port) {
    	return -1;
    }

	/**
	 *
	 * @param socket
	 * @param buffer
	 * @param port
	 * @return The amount of bytes read, 0 on no bytes available, or -1 on error
	 */
	private int handleRead(int socket, int buffer, int port) {
    	return -1;
    }

	/**
	 * Handles the tear-down between the two connections. This will first receive
	 * the FIN acknowledgement from the server and send any data in the buffer to
	 * the client and then send the client our FIN acknowledgement
	 * @param socket
	 * @return
	 */
	private int handleClose(int socket) {
		return -1;
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

	    // With the SYN packet, we don't pack any contents into the message
	    // It's a blank packet that is a type of SYN
	    byte[] contents = new byte[0];

	    // Create a message to send to the client
	    // We have modified this constructor
	    MailMessage mailMessage;

	    // The address of our machine
	    int localAddress = Machine.networkLink().getLinkAddress();

	    // Need to wrap this in a try/catch because it throws an exception
	    try {
		    mailMessage = new MailMessage(host, port, localAddress, CHAT_PORT,
				    false, false, false, true, 1, contents);
	    }
	    catch (MalformedPacketException e) {
		    e.printStackTrace();

		    return -1;
	    }

	    // Create a socket to assign to socketDescriptor
	    // A socket just holds variables so we know who we're talking to
	    Socket socket = new Socket(localAddress, port, host, port);

	    // Assign the openSocketDescriptor a Socket object that will listen for
	    // calls from this specific program that handled the connection
	    socketDescriptor[openSocketDescriptor] = socket;

	    // Send the SYN packet being wrapped by the MailMessage
	    postOffice.send(mailMessage);

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

		// Returns a new file descriptor referring to the connection, or -1 if an error occurred
		return getAvailableSocketDescriptor();
	}
}
