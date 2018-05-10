package nachos.network;

import nachos.machine.*;
import java.util.BitSet;
import java.nio.ByteBuffer;

/**
 * A mail message. Includes a packet header, a mail header, and the actual
 * payload.
 *
 * @see	nachos.machine.Packet
 */
public class MailMessage {
	public enum Type {
		DATA, FIN, STP, ACK, SYN, SYNACK, FINACK
	}
	public static final int finFlagIndex = 3;
	public static final int stpFlagIndex = 2;
	public static final int ackFlagIndex = 1;
	public static final int synFlagIndex = 0;
    /**
     * Allocate a new mail message to be sent, using the specified parameters.
     *
     * @param	dstLink		the destination link address.
     * @param	dstPort		the destination port.
     * @param	srcLink		the source link address.
     * @param	srcPort		the source port.
     * @param	fin
     * @param	stp
     * @param	ack
     * @param	syn
     * @param	seqno
     * @param	contents	the contents of the packet.
     */
    public MailMessage(int dstLink, int dstPort, int srcLink, int srcPort,
		       boolean fin, boolean stp, boolean ack, boolean syn, int seqno,
		       byte[] contents) throws MalformedPacketException {
	// make sure the paramters are valid
	if (dstPort < 0 || dstPort >= portLimit ||
	    srcPort < 0 || srcPort >= portLimit ||
	    contents.length > maxContentsLength)
	    throw new MalformedPacketException();

	this.dstPort = (byte) dstPort;
	this.srcPort = (byte) srcPort;
	transportFlags = new BitSet(4);
	transportFlags.set(3, fin);
	transportFlags.set(2, stp);
	transportFlags.set(1, ack);
	transportFlags.set(0, syn);
	this.seqno = seqno;
	this.contents = contents;

	byte[] packetContents = new byte[headerLength + contents.length];

	packetContents[0] = (byte) dstPort;
	packetContents[1] = (byte) srcPort;
	packetContents[2] = 0;
	byte[] transportFlagsByte = toByteArray(transportFlags);
	packetContents[3] = transportFlagsByte[0];
	byte[] seqnoBytes = ByteBuffer.allocate(4).putInt(seqno).array();
	for(int i = 0; i < 4; i++) {
	    packetContents[4+i] = seqnoBytes[i];
	}
	

	System.arraycopy(contents, 0, packetContents, headerLength,
			 contents.length);

	packet = new Packet(dstLink, srcLink, packetContents);
    }
    /**
     * Allocate a new mail message to be sent, using the specified parameters.
     *
     * @param	socket		the socket
     * @param	seqno
     * @param	contents
     */
    public MailMessage(Socket socket, int seqno, byte[] contents) {
	transportFlags = new BitSet(4);
	transportFlags.set(finFlagIndex, false);
	transportFlags.set(stpFlagIndex, false);
	transportFlags.set(ackFlagIndex, false);
	transportFlags.set(synFlagIndex, false);

	this.seqno = seqno;
	this.contents = contents;

	this.socket = socket;
	int dstLink = socket.getClientAddress();
	int dstPort = socket.getClientPort();
	int srcLink = socket.getHostAddress();
	int srcPort = socket.getHostPort();

	this.dstPort = (byte) dstPort;
	this.srcPort = (byte) srcPort;

	byte[] packetContents = new byte[headerLength + contents.length];

	packetContents[0] = (byte) dstPort;
	packetContents[1] = (byte) srcPort;
	packetContents[2] = 0;
	byte[] transportFlagsByte = toByteArray(transportFlags);
	packetContents[3] = transportFlagsByte[0];
	byte[] seqnoBytes = ByteBuffer.allocate(4).putInt(seqno).array();
	for(int i = 0; i < 4; i++) {
	    packetContents[4+i] = seqnoBytes[i];
	}

	System.arraycopy(contents, 0, packetContents, headerLength,
			 contents.length);

	try{
		packet = new Packet(dstLink, srcLink, packetContents);
	}
	catch(MalformedPacketException e) {

	}
    }
    /**
     * Allocate a new mail message to be sent, using the specified parameters.
     *
     * @param	socket		the socket
     * @param	type
     */
    public MailMessage(Socket socket, MailMessage.Type type) {
	transportFlags = new BitSet(4);
	switch(type) {
		case DATA:
			transportFlags.set(finFlagIndex, false);
			transportFlags.set(stpFlagIndex, false);
			transportFlags.set(ackFlagIndex, false);
			transportFlags.set(synFlagIndex, false);
		case FIN:
			transportFlags.set(finFlagIndex, true);
			transportFlags.set(stpFlagIndex, false);
			transportFlags.set(ackFlagIndex, false);
			transportFlags.set(synFlagIndex, false);
			break;
		case STP:
			transportFlags.set(finFlagIndex, false);
			transportFlags.set(stpFlagIndex, true);
			transportFlags.set(ackFlagIndex, false);
			transportFlags.set(synFlagIndex, false);
			break;
		case ACK:
			transportFlags.set(finFlagIndex, false);
			transportFlags.set(stpFlagIndex, false);
			transportFlags.set(ackFlagIndex, true);
			transportFlags.set(synFlagIndex, false);
			break;
		case SYN:
			transportFlags.set(finFlagIndex, false);
			transportFlags.set(stpFlagIndex, false);
			transportFlags.set(ackFlagIndex, false);
			transportFlags.set(synFlagIndex, true);
			break;
		case SYNACK:
			transportFlags.set(finFlagIndex, false);
			transportFlags.set(stpFlagIndex, false);
			transportFlags.set(ackFlagIndex, true);
			transportFlags.set(synFlagIndex, true);
			break;
		case FINACK:
			transportFlags.set(finFlagIndex, true);
			transportFlags.set(stpFlagIndex, false);
			transportFlags.set(ackFlagIndex, true);
			transportFlags.set(synFlagIndex, false);
			break;
	}

	seqno = 0;
	contents = new byte[0];

	this.socket = socket;
	int dstLink = socket.getClientAddress();
	int dstPort = socket.getClientPort();
	int srcLink = socket.getHostAddress();
	int srcPort = socket.getHostPort();

	this.dstPort = (byte) dstPort;
	this.srcPort = (byte) srcPort;

	byte[] packetContents = new byte[headerLength + contents.length];

	packetContents[0] = (byte) dstPort;
	packetContents[1] = (byte) srcPort;
	packetContents[2] = 0;
	byte[] transportFlagsByte = toByteArray(transportFlags);
	packetContents[3] = transportFlagsByte[0];
	byte[] seqnoBytes = ByteBuffer.allocate(4).putInt(seqno).array();
	for(int i = 0; i < 4; i++) {
	    packetContents[4+i] = seqnoBytes[i];
	}

	System.arraycopy(contents, 0, packetContents, headerLength,
			 contents.length);

	try {
	packet = new Packet(dstLink, srcLink, packetContents);
	}
	catch(MalformedPacketException e) {

	}
    }
    /**
     * Allocate a new mail message to be sent, using the specified parameters.
     *
     * @param	socket		the socket
     * @param	fin
     * @param	stp
     * @param	ack
     * @param	syn
     * @param	seqno
     * @param	contents	the contents of the packet.
     */
    public MailMessage(Socket socket,
		       boolean fin, boolean stp, boolean ack, boolean syn, int seqno,
		       byte[] contents) throws MalformedPacketException {
	this.socket = socket;
	int dstLink = socket.getClientAddress();
	int dstPort = socket.getClientPort();
	int srcLink = socket.getHostAddress();
	int srcPort = socket.getHostPort();
	// make sure the paramters are valid
	if (dstPort < 0 || dstPort >= portLimit ||
	    srcPort < 0 || srcPort >= portLimit ||
	    contents.length > maxContentsLength)
	    throw new MalformedPacketException();

	this.dstPort = (byte) dstPort;
	this.srcPort = (byte) srcPort;
	transportFlags = new BitSet(4);
	transportFlags.set(3, fin);
	transportFlags.set(2, stp);
	transportFlags.set(1, ack);
	transportFlags.set(0, syn);
	this.seqno = seqno;
	this.contents = contents;

	byte[] packetContents = new byte[headerLength + contents.length];

	packetContents[0] = (byte) dstPort;
	packetContents[1] = (byte) srcPort;
	packetContents[2] = 0;
	byte[] transportFlagsByte = toByteArray(transportFlags);
	packetContents[3] = transportFlagsByte[0];
	byte[] seqnoBytes = ByteBuffer.allocate(4).putInt(seqno).array();
	for(int i = 0; i < 4; i++) {
	    packetContents[4+i] = seqnoBytes[i];
	}
	

	System.arraycopy(contents, 0, packetContents, headerLength,
			 contents.length);

	packet = new Packet(dstLink, srcLink, packetContents);
    }

    /**
     * Allocate a new mail message using the specified packet from the network.
     *
     * @param	packet	the packet containg the mail message.
     */
    public MailMessage(Packet packet) throws MalformedPacketException {
	this.packet = packet;
	
	// make sure we have a valid header
	if (packet.contents.length < headerLength ||
	    packet.contents[0] < 0 || packet.contents[0] >= portLimit ||
	    packet.contents[1] < 0 || packet.contents[1] >= portLimit)
	    throw new MalformedPacketException();

	dstPort = packet.contents[0];
	srcPort = packet.contents[1];
	byte[] transportFlagsByte = {packet.contents[3]};
	for(int i = 0; i < 4; i++) {
	    transportFlags.set(i,((transportFlagsByte[0] & (byte)(0x1 << i)) >> i) == 0);
	}
	byte[] seqnoBytes = new byte[4];
	for(int i = 0; i < 4; i++) {
	    seqnoBytes[i] = packet.contents[4+i];
	}
	seqno = ByteBuffer.wrap(seqnoBytes).getInt();

	contents = new byte[packet.contents.length - headerLength];
	System.arraycopy(packet.contents, headerLength, contents, 0,
			 contents.length);
    }

	public static byte[] toByteArray(BitSet bits) {
	    byte[] bytes = new byte[(bits.length() + 7) / 8];
	    for (int i=0; i<bits.length(); i++) {
		if (bits.get(i)) {
		    bytes[bytes.length-i/8-1] |= 1<<(i%8);
		}
	    }
	    return bytes;
	}

    /**
     * Return a string representation of the message headers.
     */
    public String toString() {
	return "from (" + packet.srcLink + ":" + srcPort +
	    ") to (" + packet.dstLink + ":" + dstPort +
	    "), " + contents.length + " bytes";
    }

    public boolean isData() {
	return (!transportFlags.get(3)) && (!transportFlags.get(2)) && (!transportFlags.get(1)) && (!transportFlags.get(0));
    }
    public boolean isFin() {
	return transportFlags.get(3) && (!transportFlags.get(2)) && (!transportFlags.get(1)) && (!transportFlags.get(0));
    }
    public boolean isStp() {
	return (!transportFlags.get(3)) && transportFlags.get(2) && (!transportFlags.get(1)) && (!transportFlags.get(0));
    }
    public boolean isAck() {
	return (!transportFlags.get(3)) && (!transportFlags.get(2)) && transportFlags.get(1) && (!transportFlags.get(0));
    }
    public boolean isSyn() {
	return (!transportFlags.get(3)) && (!transportFlags.get(2)) && (!transportFlags.get(1)) && transportFlags.get(0);
    }
    public boolean isSynAck() {
	return (!transportFlags.get(3)) && (!transportFlags.get(2)) && transportFlags.get(1) && transportFlags.get(0);
    }
    public boolean isFinAck() {
	return transportFlags.get(3) && (!transportFlags.get(2)) && transportFlags.get(1) && (!transportFlags.get(0));
    }
    public Type getType() {
	if(this.isData())
		return MailMessage.Type.DATA;
	else if(this.isFin())
		return MailMessage.Type.FIN;
	else if(this.isStp())
		return MailMessage.Type.STP;
	else if(this.isAck())
		return MailMessage.Type.ACK;
	else if(this.isSyn())
		return MailMessage.Type.SYN;
	else if(this.isSynAck())
		return MailMessage.Type.SYNACK;
	else if(this.isFinAck())
		return MailMessage.Type.FINACK;
	return MailMessage.Type.DATA;
    }
    public Socket getSocket() {
	return socket;
    }

    /** */
    public Socket socket;
    /** This message, as a packet that can be sent through a network link. */
    public Packet packet;
    /** The port used by this message on the destination machine. */
    public int dstPort;
    /** The port used by this message on the source machine. */
    public int srcPort;
    /** */
    public BitSet transportFlags;
    public int seqno;
    /** The contents of this message, excluding the mail message header. */
    public byte[] contents;

    /**
     * The number of bytes in a mail header. The header is formatted as
     * follows:
     *
     * <table>
     * <tr><td>offset</td><td>size</td><td>value</td></tr>
     * <tr><td>0</td><td>1</td><td>destination port</td></tr>
     * <tr><td>1</td><td>1</td><td>source port</td></tr>
     * </table>
     */
    public static final int headerLength = 8;

    /** Maximum payload (real data) that can be included in a single mesage. */
    public static final int maxContentsLength =
	Packet.maxContentsLength - headerLength;

    /**
     * The upper limit on mail ports. All ports fall between <tt>0</tt> and
     * <tt>portLimit - 1</tt>.
     */    
    public static final int portLimit = 128;
}
