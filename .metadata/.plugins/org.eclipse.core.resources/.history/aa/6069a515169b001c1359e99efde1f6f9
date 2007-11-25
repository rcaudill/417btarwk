
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
public class Peer {
	
	public boolean am_choking; //this client is choking the peer
	public boolean am_interested; //this client is interested in the peer
	public boolean peer_choking; //peer is choking this client
	public boolean peer_interested; //peer is interested in this client
	
	public byte pstrlen; //string length of <pstr>, as a single raw byte
	public String pstr; //string identifier of the protocol
	public byte[] reserved = new byte[8]; //eight (8) reserved bytes.
	public byte[] info_hash = new byte[20]; //20-byte SHA1 hash of the info key in the metainfo file
	public byte[] peer_id = new byte[20]; //20-byte string used as a unique ID for the client
	
	public String handshake; //handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
	
	/**
	 * @param pstr string identifier of the protocol
	 * @param reserved eight (8) reserved bytes. All current implementations use all zeroes. Each bit in these bytes can be used to change the behavior of the protocol. An email from Bram suggests that trailing bits should be used first, so that leading bits may be used to change the meaning of trailing bits. 
	 * @param info_hash 20-byte SHA1 hash of the info key in the metainfo file. This is the same info_hash that is transmitted in tracker requests.
	 * @param peer_id 20-byte string used as a unique ID for the client. This is usually the same peer_id that is transmitted in tracker requests (but not always e.g. an anonymity option in Azureus).
	 */
	public Peer(String pstr, String reserved, String info_hash, String peer_id) {
		// Client connections start out as "choked" and "not interested"
		this.am_choking = true;
		this.am_interested = false;
		this.peer_choking = true;
		this.peer_interested = false;

		try {
			this.pstr = pstr;
			this.pstrlen = (byte)this.pstr.length();

			this.reserved = reserved.getBytes();
			this.info_hash = info_hash.getBytes();
			this.peer_id = peer_id.getBytes();
		} catch(Exception e) {
			System.out.println("Failed to initialze peer. Check for variable size overflow");
			System.exit(1);
		}
		
		try {
			//handshake: <pstrlen><pstr><reserved><info_hash><peer_id>
			this.handshake = new String(pstrlen + pstr + reserved + info_hash + peer_id);
		} catch( Exception e) {
			System.out.println("Failed to create peer handshake.");
			System.exit(1);
		}
	}
}
