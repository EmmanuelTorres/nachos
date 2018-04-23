package nachos.network;

import nachos.machine.*;
import nachos.security.Privilege;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.io.IOException;
import java.net.DatagramPacket;

/**
 * A <tt>VMProcess</tt> that supports networking syscalls.
 */
public class NetProcess extends UserProcess {
    /**
     * Allocate a new process.
     */
    public NetProcess() {
	super();
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
	 * @return The amount of bytes read, 0 on no bytes avialable, or -1 on error
	 */
	private int handleRead(int socket, int buffer, int port) {
    	return -1;
    }


    private int handleClose(int socket) {
		return -1;
    }

	/**
	 * Attempt to initiate a new connection to the specified port on the specified
	 * remote host, and return a new file descriptor referring to the connection.
	 * connect() does not give up if the remote host does not respond immediately.
	 *
	 * Returns the new file descriptor, or -1 if an error occurred.
	 */
    private int handleConnect(int host, int port) {
	    /*
	     * One endpoint(client) calls the connect() system call. This endpoint is referred to as the active
	     * endpoint. A process at the other endpoint(server) must later call the accept() system call.
	     * As these system calls are invoked, the underlying protocol initiates a 2-way handshake.
	     * When a user process invokes the connect() system call, the active endpoint sends a
	     * synchronize packet (SYN). This causes a pending connection to be created. The state for
	     * this pending connection must be stored at the receiver (server?) until a user process
	     * at the passive endpoint invokes the accept() system call. When accept() is invoked, the
	     * passive end sends a SYN acknowledgement packet (SYN/ACK) back to the active endpoint
	     * and the connection is established.
	     */

	    // One endpoint calls the connect system call, invoking this function
	    // The endpoint that called connect is the active endpoint
	    // We are inside the passive endpoint
	    //

	    // The first thing we do is check if there is availability for a new NetworkLink
	    // object that will be put on a fileDescriptor
	    int openFileDescriptor = getAvailableFileDescriptor();

	    // If there isn't, we return -1
	    if (openFileDescriptor == -1)
	    {
	    	return openFileDescriptor;
	    }

	    byte[] contents = new byte[0];

	    Packet synchronizationPacket = null;
	    MailMessage mailMessage = null;

	    try
	    {
	    	// Packet(dstLink, srcLink, contents)
		    // Synchronization (SYN) packet to be sent to the client
		    synchronizationPacket = new Packet(host, Machine.networkLink().getLinkAddress(), contents);
		    // MailMessage that wraps the SYN packet
		    mailMessage = new MailMessage(synchronizationPacket);
	    }
	    catch (MalformedPacketException e)
	    {
		    e.printStackTrace();
	    }

	    if (synchronizationPacket == null || mailMessage == null)
	    {
		    return -1;
	    }

	    // Assign the openFileDescriptor a NetworkLink object that will listen for
	    // calls from this specific program that handled the connection
	    // NetworkLink takes a Privilege object as a parameter -- assigned by Nachos?
	    fileDescriptors[openFileDescriptor] = new NetworkLink(new Privilege());

	    // Initialize a PostOffice
	    PostOffice postOffice = new PostOffice();

	    // Send the SYN packet being wrapped by the MailMessage
	    postOffice.send(mailMessage);

	    // Return a new file descriptor referring to the connection
	    return getAvailableFileDescriptor();
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
		return getAvailableFileDescriptor();
	}
}
