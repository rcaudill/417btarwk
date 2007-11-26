
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
	private final String pstr = "BitTorrent Protocol";
	private final byte pstrlen = (byte)pstr.length();
	
	public boolean am_choking; //this client is choking the peer
	public boolean am_interested; //this client is interested in the peer
	public boolean peer_choking; //peer is choking this client
	public boolean peer_interested; //peer is interested in this client
	
	public byte[] info_hash = new byte[20]; //20-byte SHA1 hash of the info key in the metainfo file
	public byte[] peer_id = new byte[20]; //20-byte string used as a unique ID for the client
	public String ip;
	public int port;
	
	public byte[] my_peer_id = new byte[20];
	
	public String handshake; //handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
	
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

		try {

			this.info_hash = info_hash;
			this.peer_id = peer_id;
			this.my_peer_id = my_peer_id;
			this.ip = ip;
			this.port = port;
			
		} catch(Exception e) {
			System.out.println("Failed to initialze peer. Check for variable size overflow");
			System.exit(1);
		}
		
		try {
			//handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
			byte[] tempHandshake = new byte[1+pstrlen+8+20+20];
			
			// Add <pstrlen> portion:
			tempHandshake[0] = pstrlen;
			
			// Add <pstr> portion:
			byte[] pstrBytes = pstr.getBytes();
			for(int i = 0; i < pstrlen; i++)
				tempHandshake[1 + i] = pstrBytes[i];
			
			// Add <reserved> portion:
			for(int i = 0; i < 8; i++)
				tempHandshake[1 + pstrlen + i] = 0;
			
			// Add <info_hash> portion:
			for(int i = 0; i < 20; i++)
				tempHandshake[9 + pstrlen + i] = this.info_hash[i];
			
			// Add <peer_id> portion:
			for(int i = 0; i < 20; i++)
				tempHandshake[29 + pstrlen + i] = this.peer_id[i];
			
			this.handshake = new String(tempHandshake);
		} catch( Exception e) {
			System.out.println("Failed to create peer handshake.");
			System.exit(1);
		}
	}
}
