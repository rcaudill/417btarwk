import java.nio.*;
import java.nio.channels.*;
import java.io.*;
import java.util.*;

/**
 * 
 * am_choking: this client is choking the peer
 * am_interested: this client is interested in the peer
 * peer_choking: peer is choking this client
 * peer_interested: peer is interested in this client
 * 
 * pstrlen: string length of <pstr>, as a single raw byte
 * pstr: string identifier of the protocol
 * reserved: eight (8) reserved bytes. All current implementations use all zeroes. Each bit in these bytes can be used to change the behavior of the protocol. An email from Bram suggests that trailing bits should be used first, so that leading bits may be used to change the meaning of trailing bits. 
 * info_hash: 20-byte SHA1 hash of the info key in the metainfo file. This is the same info_hash that is transmitted in tracker requests.
 * peer_id: 20-byte string used as a unique ID for the client. This is usually the same peer_id that is transmitted in tracker requests (but not always e.g. an anonymity option in Azureus).
 *
 * handshake: The handshake is a required message and must be the first message transmitted by the client. It is (49+len(pstr)) bytes long. handshake: <pstrlen><pstr><reserved><info_hash><peer_id> 
 */
public class Peer 
{
	// Constants:
	private final int BYTES_TO_ALLOCATE = 1024;
	private final String pstr = "BitTorrent protocol";
	private final byte pstrlen = (byte)(pstr.length());
	
	// Information about peer:
	public byte[] info_hash = new byte[20]; //20-byte SHA1 hash of the info key in the metainfo file
	public byte[] peer_id = new byte[20]; //20-byte string used as a unique ID for the client
	public String ip;
	public int port;
	
	public byte[] my_peer_id = new byte[20];
	
	public byte[] handshake = new byte[49 + pstrlen]; //handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
	
	
	// State for transfers:
	public boolean am_choking; //this client is choking the peer
	public boolean am_interested; //this client is interested in the peer
	public boolean peer_choking; //peer is choking this client
	public boolean peer_interested; //peer is interested in this client
	
	public boolean handshake_sent; // this client sent a handshake to the peer
	public boolean handshake_received; // this client received a handshake from the peer
	
	public ByteBuffer readBuffer;
	
	public int bytesLeft;
	public BitSet parts; // the parts that the peer has
	
	
	/**
	 * @param info_hash 20-byte SHA1 hash of the info key in the metainfo file. This is the same info_hash that is transmitted in tracker requests.
	 * @param peer_id 20-byte string that is being used by the other peer as their peer id.
	 * @param my_peer_id 20-byte string used as a unique ID for this client. This is usually the same peer_id that is transmitted in tracker requests (but not always e.g. an anonymity option in Azureus).
	 * @param ip IP address for the specific peer
	 * @param port Port to use when contacting the specific peer
	 */
	public Peer(byte[] info_hash, byte[] peer_id, byte[] my_peer_id, String ip, int port) {
		// Client connections start out as "choked" and "not interested"
		this.am_choking = true;
		this.am_interested = false;
		this.peer_choking = true;
		this.peer_interested = false;
		this.handshake_received = false;
		this.handshake_sent = false;
		
		this.readBuffer = ByteBuffer.allocate(BYTES_TO_ALLOCATE);
		this.bytesLeft = 0;
		
		this.parts = new BitSet();
		
		try
		{
			this.info_hash = info_hash;
			this.peer_id = peer_id;
			this.my_peer_id = my_peer_id;
			this.ip = ip;
			this.port = port;
		}
		catch(Exception e)
		{
			System.out.println("Failed to initialze peer. Check for variable size overflow");
			System.exit(1);
		}
		
		try
		{
			//handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
			ByteBuffer byteBuffer = ByteBuffer.allocate(49 + pstr.length());
			byteBuffer.put(pstrlen);
			byteBuffer.put(pstr.getBytes());
			byteBuffer.put(new byte[] {0,0,0,0,0,0,0,0});
			byteBuffer.put(this.info_hash);
			byteBuffer.put(this.peer_id);
			this.handshake = byteBuffer.array();
			
			//System.out.println("IP is" + ip);
			//System.out.println("Handshake is " + Peer.getBytesAsHex(handshake));
		}
		catch(Exception e)
		{
			System.out.println("Failed to create peer handshake.");
			System.exit(1);
		}
	}
	
	public String toString() {
		return "( INFO_HASH:" + getBytesAsHex(info_hash) + ", PEER_ID:" + getBytesAsHex(peer_id) + ", " + ip + ":" + port + ")\n";
	}
	
	public static String getBytesAsHex(byte[] bytes) {
		String output = new String();
		for ( byte b : bytes ) {
			output += Integer.toHexString( b & 0xff ) + " " ;
		}
		return output;
	}
	
	public boolean equals(Object o)
	{
		if(o instanceof Peer)
		{
			Peer other = (Peer)o;
			
			return (other.ip.equals(this.ip) && other.port == this.port && ByteBuffer.wrap(other.peer_id).equals(ByteBuffer.wrap(this.peer_id)) && ByteBuffer.wrap(other.info_hash).equals(ByteBuffer.wrap(this.info_hash)));
		}
		return false;
	}
	
	public boolean readAndParse(SocketChannel socketChannel, boolean readFirst)
	{
		boolean cont = true;
		if(readFirst)
		{
			// Information has not been read from the SocketChannel yet.. Do so
			try
			{
				this.bytesLeft += socketChannel.read(readBuffer);
				readBuffer.position(0);
			}
			catch(IOException e)
			{
				return false;
			}
		}
		while(this.bytesLeft >= 4 && cont)
		{
			// Attempt to read (may be a partial message)
			
			// While there are still messages in the queue:
			int length = readBuffer.getInt(0);
			
			if(length == 0)
			{
				// Keep-Alive message
				readBuffer.position(4);
				readBuffer.compact();
				readBuffer.position(0);
				
				this.bytesLeft -= 4;
			}
			else if(length >= 1 && this.bytesLeft >= 5)
			{
				byte id = readBuffer.get(4);
				if(id == 0)
				{
					// Choke Message Received:
					
					// Handle choke message:
					this.peer_choking = true;
					
					// Perform state cleanup:
					readBuffer.position(5);
					readBuffer.compact();
					readBuffer.position(0);
					
					this.bytesLeft -= 5;
				}
				else if(id == 1)
				{
					// Un-choke Message Received:
					
					// Handle un-choke message:
					this.peer_choking = false;
					
					// Perform state cleanup:
					readBuffer.position(5);
					readBuffer.compact();
					readBuffer.position(0);
					
					this.bytesLeft -= 5;
				}
				else if(id == 2)
				{
					// Interested Message Received:
					
					// Handle Interested message:
					this.peer_interested = true;
					
					// Perform state cleanup:
					readBuffer.position(5);
					readBuffer.compact();
					readBuffer.position(0);
					
					this.bytesLeft -= 5;
				}
				else if(id == 3)
				{
					// Not Interested Message Received:
					
					// Handle Not Interested  message:
					this.peer_interested = false;
					
					// Perform state cleanup:
					readBuffer.position(5);
					readBuffer.compact();
					readBuffer.position(0);
					
					this.bytesLeft -= 5;
				}
				else if(id == 4)
				{
					if(this.bytesLeft < 9)
					{
						cont = false;
					}
					else
					{
						// Have Message Received:
						
						// Handle Have message:
						int piece_index = readBuffer.getInt(5);
						
						// More
						
						// Perform state cleanup:
						readBuffer.position(9);
						readBuffer.compact();
						readBuffer.position(0);
						
						this.bytesLeft -= 9;
					}
				}
				else if(id == 5)
				{
					if(this.bytesLeft < length + 4) // There might be a better way... (check the actual length of the file?)
					{
						cont = false;
					}
					else
					{
						// Bitfield Message Received:
						
						// Handle Bitfield message:
						byte[] ba = new byte[length - 1];
						readBuffer.position(5);
						readBuffer.get(ba);
						this.parts = BitTortoise.bitSetFromByteArray(ba);
						
						// More??
						
						// Perform state cleanup:
						readBuffer.position(length + 4);
						readBuffer.compact();
						readBuffer.position(0);
						
						this.bytesLeft -= (length + 4);
					}
				}
				else if(id == 6)
				{
					if(this.bytesLeft < 17)
					{
						cont = false;
					}
					else
					{
						// Request Message Received:
						
						// Handle Request message:
						int request_index = readBuffer.getInt(5);
						int request_begin = readBuffer.getInt(9);
						int request_length = readBuffer.getInt(13);
						
						// More
						
						// Perform state cleanup:
						readBuffer.position(17);
						readBuffer.compact();
						readBuffer.position(0);
						
						this.bytesLeft -= 17;
					}
				}
				else if(id == 7)
				{
					// Note: handle size somehow...
					
					// Piece Message Received:
					
					// Handle Piece message:
					int piece_index = readBuffer.getInt(5);
					int piece_begin = readBuffer.getInt(9);
					
					byte[] block;
					
					// More
					
					// Perform state cleanup:
					readBuffer.position(length + 4);
					readBuffer.compact();
					readBuffer.position(0);
					
					this.bytesLeft -= (length + 4);
				}
				else if(id == 8)
				{
					if(this.bytesLeft < 17)
					{
						cont = false;
					}
					else
					{
						// Cancel Message Received:
						
						// Handle Cancel message:
						int cancel_index = readBuffer.getInt(5);
						int cancel_begin = readBuffer.getInt(9);
						int cancel_length = readBuffer.getInt(13);
						
						
						// More
						
						// Perform state cleanup:
						readBuffer.position(17);
						readBuffer.compact();
						readBuffer.position(0);
						
						this.bytesLeft -= 17;
					}
				}
				else
				{
					// Unrecognized id, ignore the rest of length bytes
				}
			}
		}
		
		if(this.bytesLeft >= 0)
		{
			readBuffer.position(this.bytesLeft);
		}
		
		return true;
	}
}