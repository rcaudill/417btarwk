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
	public BitSet completedPieces; // the parts that the peer this object represents has (renamed so its purpose is more obvious)
	
	// Information about this client:
	public byte[] my_peer_id = new byte[20];
	
	// Other information:
	public byte[] handshake = new byte[49 + pstrlen]; //handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
	
	// State for transfers:
	public boolean am_choking; //this client is choking the peer
	public boolean shouldUnchoke; //this is for when a peer will be unchoked the next time it is writable
	public boolean shouldChoke; //this is for when a peer will be choked the next time it is writable
	public boolean am_interested; //this client is interested in the peer
	public boolean shouldInterest; // this is for when a peer will be sent an interested message the next time it is writable
	public boolean shouldUninterest; // this is for when a peer will be sent an uninterested message the next time it is writable
	
	public boolean peer_choking; //peer is choking this client
	public boolean peer_interested; //peer is interested in this client
	
	public boolean handshake_sent; // this client sent a handshake to the peer
	public boolean handshake_received; // this client received a handshake from the peer
	
	public boolean sent_bitfield;
	public List<BlockRequest> receiveRequests; // Pieces that this client is sending out (received requests)
	public List<BlockRequest> sendRequests; // Requests that this client is sending out
	public BlockRequest blockRequest = null; //the block that you have requested for this peer to send you.
	public BlockRequest shouldCancel; // The block that we (this client) wish to cancel the next time we hit a sendMessage.  Remove this from the sendRequests before setting this!
	
	public long lastMessageSentTime;
	// Status holders for what is being currently read
	public ByteBuffer readBuffer;
	public int bytesLeft;
	public int bytesReadThisRound;
	
	public ByteBuffer sendBuffer;
	public int unsent;
	
	public BitSet advertisedPieces; // Pieces that we (this client) have advertised to other peers
	
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
		this.shouldUnchoke = false;
		this.shouldChoke = false;
		this.receiveRequests = new ArrayList<BlockRequest>();
		this.sendRequests = new ArrayList<BlockRequest>();
		this.sent_bitfield = false;
		this.shouldCancel = null;
		
		this.readBuffer = ByteBuffer.allocate(BYTES_TO_ALLOCATE);
		this.bytesLeft = 0;
		
		this.sendBuffer = null;
		this.unsent = 0;
		
		this.completedPieces = new BitSet();
		
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
			System.err.println("Failed to initialze peer. Check for variable size overflow");
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
			byteBuffer.put(this.my_peer_id);
			this.handshake = byteBuffer.array();
		}
		catch(Exception e)
		{
			System.err.println("Failed to create peer handshake.");
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
	
	/**
	 * Sends a message to this peer, if one is in need of being sent.
	 * 
	 * @param sc the SocketChannel on which we should send a message
	 * @param completedPieces the pieces that we have already completed
	 * @return whether there were any IOExceptions thrown that mean we should stop communicating with this peer
	 */
	public boolean sendMessage(SocketChannel sc, BitSet completedPieces)
	{
		// Determine what it is that we need to send, if anything:
		if(this.unsent > 0)
		{
			// We didn't finish sending everything last time, so send the rest (or as much as possible):
			try
			{
				int sent = sc.write(this.sendBuffer);
				this.unsent -= sent;
				
				lastMessageSentTime = (new Date()).getTime();
			}
			catch(IOException e)
			{
				return false;
			}
		}
		else if(this.handshake_received)
		{
			// If we've gotten a handshake from them, we can do something:
			
			if(this.handshake_sent)
			{
				// If we've sent a handshake to them, we have completed the handshake
				if(completedPieces.isEmpty() && !this.sent_bitfield)
				{
					try
					{
						byte[] bytesToSend = MessageLibrary.getBitfieldMessage(BitTortoise.byteArrayFromBitSet(completedPieces, completedPieces.length()));
						this.sendBuffer = ByteBuffer.wrap(bytesToSend);
						int sent = sc.write(this.sendBuffer);
						this.unsent = bytesToSend.length - sent;
						
						this.sent_bitfield = true;
						
						lastMessageSentTime = (new Date()).getTime();
					}
					catch(IOException e)
					{
						return false;
					}
				}
				else if(this.shouldChoke || this.shouldUnchoke)
				{
					if(this.shouldChoke)
					{
						try
						{
							this.sendBuffer = ByteBuffer.wrap(MessageLibrary.choke);
							int sent = sc.write(this.sendBuffer);
							this.unsent = MessageLibrary.choke.length - sent;
							
							this.am_choking = true;
							
							lastMessageSentTime = (new Date()).getTime();
						}
						catch(IOException e)
						{
							return false;
						}
					}
					else // if(this.shouldUnchoke)
					{
						try
						{
							this.sendBuffer = ByteBuffer.wrap(MessageLibrary.unchoke);
							int sent = sc.write(this.sendBuffer);
							this.unsent = MessageLibrary.unchoke.length - sent;
							
							this.am_choking = false;
							
							lastMessageSentTime = (new Date()).getTime();
						}
						catch(IOException e)
						{
							return false;
						}
					}
				}
				else if(this.shouldInterest || this.shouldUninterest)
				{
					if(this.shouldInterest)
					{
						try
						{
							this.sendBuffer = ByteBuffer.wrap(MessageLibrary.interested);
							int sent = sc.write(this.sendBuffer);
							this.unsent = MessageLibrary.interested.length - sent;
							
							this.am_interested = true;
							
							lastMessageSentTime = (new Date()).getTime();
						}
						catch(IOException e)
						{
							return false;
						}
					}
					else // if(this.shouldUninterest)
					{
						try
						{
							this.sendBuffer = ByteBuffer.wrap(MessageLibrary.not_interested);
							int sent = sc.write(this.sendBuffer);
							this.unsent = MessageLibrary.not_interested.length - sent;
							
							this.am_interested = false;
							
							lastMessageSentTime = (new Date()).getTime();
						}
						catch(IOException e)
						{
							return false;
						}
					}
				}
				else if(this.shouldCancel != null)
				{
					// Send a cancel message
					try
					{
						byte[] bytesToSend = MessageLibrary.getCancelMessage(this.shouldCancel.piece, this.shouldCancel.offset, this.shouldCancel.length);
						this.sendBuffer = ByteBuffer.wrap(bytesToSend);
						int sent = sc.write(this.sendBuffer);
						this.unsent = bytesToSend.length - sent;
						
						this.shouldCancel = null;
						
						lastMessageSentTime = (new Date()).getTime();
					}
					catch(IOException e)
					{
						return false;
					}
				}
				else
				{
					if(this.am_interested && !this.peer_choking)
					{
						// Send the next unsent request message:
						for(BlockRequest br : this.sendRequests)
						{
							if(br.status == BlockRequest.UNREQUESTED)
							{
								try
								{
									byte[] bytesToSend = MessageLibrary.getRequestMessage(br.piece, br.offset, br.length);
									this.sendBuffer = ByteBuffer.wrap(bytesToSend);
									int sent = sc.write(this.sendBuffer);
									this.unsent = bytesToSend.length - sent;
									
									br.status = BlockRequest.REQUESTED;
									
									lastMessageSentTime = (new Date()).getTime();
								}
								catch(IOException e)
								{
									return false;
								}
								
								return true;
							}
						}
					}
					else if(!this.am_choking && this.receiveRequests.size() != 0)
					{
						// Respond to a request for data with a Piece message
						try
						{
							BlockRequest br = this.receiveRequests.remove(0);
							byte[] bytesToSend = BitTortoise.getPiece(br.piece, br.offset, br.length);
							this.sendBuffer = ByteBuffer.wrap(bytesToSend);
							int sent = sc.write(this.sendBuffer);
							this.unsent = bytesToSend.length - sent;
							
							lastMessageSentTime = (new Date()).getTime();
						}
						catch(IOException e)
						{
							return false;
						}
					}
					else
					{
						// Advertise new blocks that we have gotten
						BitSet newPiecesToAdvertise = (BitSet)completedPieces.clone();
						newPiecesToAdvertise.andNot(this.advertisedPieces);
						if(!newPiecesToAdvertise.isEmpty())
						{
							// Advertise ones that they haven't reported having first:
							BitSet theyDontHave = (BitSet)newPiecesToAdvertise.clone();
							theyDontHave.andNot(this.completedPieces);
							if(!theyDontHave.isEmpty())
								newPiecesToAdvertise = theyDontHave;
							
							// Pick a random one from newPiecesToAdvertise to send in a have message
							int toSend = -1;
							while(toSend == -1)
								toSend = newPiecesToAdvertise.nextSetBit((int)(Math.random()*newPiecesToAdvertise.length()));
							try
							{
								byte[] bytesToSend = MessageLibrary.getHaveMessage(toSend);
								this.sendBuffer = ByteBuffer.wrap(bytesToSend);
								int sent = sc.write(this.sendBuffer);
								this.unsent = bytesToSend.length - sent;
								
								this.advertisedPieces.set(toSend);
								
								lastMessageSentTime = (new Date()).getTime();
							}
							catch(IOException e)
							{
								return false;
							}
							
							return true;
						}
						
						long now = (new Date()).getTime();
						if(now - lastMessageSentTime >= 2 * 60000) // if it has been 2 minutes, send a keep_alive message
						{
							// Otherwise, if there has been enough time since the last time a message was sent, send a keep-alive message
							try
							{
								this.sendBuffer = ByteBuffer.wrap(MessageLibrary.keep_alive);
								int sent = sc.write(this.sendBuffer);
								this.unsent = MessageLibrary.keep_alive.length - sent;
								
								lastMessageSentTime = (new Date()).getTime();
							}
							catch(IOException e)
							{
								return false;
							}
						}
					}
				}
			}
			else if(!this.handshake_sent)
			{
				// We have not yet sent them a handshake in response to their handshake, do so now:
				try
				{
					this.sendBuffer = ByteBuffer.wrap(this.handshake);
					int sent = sc.write(this.sendBuffer);
					this.unsent = this.handshake.length - sent;
					
					lastMessageSentTime = (new Date()).getTime();
				}
				catch(IOException e)
				{
					return false;
				}
			}
		}
		
		return true;
	}
}
