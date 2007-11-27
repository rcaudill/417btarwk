import java.nio.ByteBuffer;


public class HelloWorld {
	public static void main(String [] args) {
		System.out.println("hello world");
		System.out.println("hello andrew");
		
		// Handshake Message Received:
		byte[] bytes = (new String("" + ((char)19) + "BitTorrent protocol" + new String(new byte[]{0,0,0,0,0,0,0,0}) + new String(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,14,13,12,11,1}) + new String(new byte[]{15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0,1,2,3,4}))).getBytes();
		ByteBuffer b = ByteBuffer.allocate(100);
		
		b.put(bytes);
		
		byte[] external_info_hash = new byte[20];
		byte[] external_peer_id = new byte[20];
		
		b.position(28);
		b.get(external_info_hash, 0, 20);
		
		b.position(48);
		b.get(external_peer_id, 0, 20);
		
		System.out.println(Peer.getBytesAsHex(external_info_hash));
		System.out.println(Peer.getBytesAsHex(external_peer_id));
		
		System.out.println("" + (byte)b.get(0) + "       hello -" + b.array()[0] + " " + ByteBuffer.wrap(external_info_hash).equals(ByteBuffer.wrap(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,14,13,12,11,1})));
	}
}
