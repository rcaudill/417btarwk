import java.nio.ByteBuffer;

public class MessageLibrary {

	public final byte[] keep_alive = 	"\0\0\0\0".getBytes();
	public final byte[] choke = 			"\0\0\0\1\0".getBytes();
	public final byte[] unchoke = 		"\0\0\0\1\1".getBytes();
	public final byte[] interested = 	"\0\0\0\1\2".getBytes();
	public final byte[] not_interested = "\0\0\0\1\3".getBytes();

	private ByteBuffer have;
	private ByteBuffer bitfield;
	private ByteBuffer request;
	private ByteBuffer piece;
	private ByteBuffer cancel;
	private ByteBuffer port;
	
	public byte[] getHaveMessage(byte piece_index) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.have = ByteBuffer.allocate(6);
		this.have.put("\0\0\0\5\4".getBytes());
		this.have.put(piece_index);
		return this.have.array();
	}
	public byte[] getBitfieldMessage(byte x, byte bitfield_parameter) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.bitfield = ByteBuffer.allocate(8);
		this.bitfield.put("\0\0\0".getBytes());
		this.bitfield.put( (byte) (((byte) 1)+ (x)) );
		this.bitfield.put( (byte) 5);
		this.bitfield.put(bitfield_parameter);
		return this.bitfield.array();
	}
	public byte[] getRequestMessage(byte index, byte begin, byte length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.request = ByteBuffer.allocate(8);
		this.request.put("\0\0\1\3\6".getBytes());
		this.request.put(index);
		this.request.put(begin);
		this.request.put(length);
		return this.request.array();
	}
	// Not done yet
	public byte[] getPieceMessage(byte x, byte index, byte begin, byte length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.piece = ByteBuffer.allocate(1);
		this.piece.put("\0\0\0\1\7".getBytes());
		return this.piece.array();
	}
	// Not done yet
	public byte[] getCancelMessage(byte x, byte index, byte begin, byte length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.cancel = ByteBuffer.allocate(1);
		this.cancel.put("\0\0\1\3\b".getBytes());
		return this.cancel.array();
	}
	// Not done yet
	public byte[] getPortMessage(byte x, byte index, byte begin, byte length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.port = ByteBuffer.allocate(1);
		this.port.put("\0\0\0\3\t".getBytes());
		return this.port.array();
	}
	
	public static void main(String[] args) {
		MessageLibrary ml = new MessageLibrary();
		
		System.out.println(ml.getBytesAsHex(ml.keep_alive));
		System.out.println(ml.getBytesAsHex(ml.choke));
		System.out.println(ml.getBytesAsHex(ml.unchoke));
		System.out.println(ml.getBytesAsHex(ml.interested));
		System.out.println(ml.getBytesAsHex(ml.not_interested));
	}

	private String getBytesAsHex(byte[] bytes) {
		String output = new String();
		for ( byte b : bytes ) {
			output += Integer.toHexString( b & 0xff ) + " " ;
		}
		return output;
	}

}
