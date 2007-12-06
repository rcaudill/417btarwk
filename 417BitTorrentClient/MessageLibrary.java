import java.nio.ByteBuffer;

public class MessageLibrary
{

	public static final byte[] keep_alive = 		new byte[]{0,0,0,0};
	public static final byte[] choke = 			new byte[]{0,0,0,1,0};
	public static final byte[] unchoke = 			new byte[]{0,0,0,1,1};
	public static final byte[] interested = 		new byte[]{0,0,0,1,2};
	public static final byte[] not_interested =	new byte[]{0,0,0,1,3};

	static private ByteBuffer have;
	static private ByteBuffer bitfield;
	static private ByteBuffer request;
	static private ByteBuffer piece;
	static private ByteBuffer cancel;
//	private ByteBuffer port;
	
	public static byte[] getHaveMessage(int piece_index) {
		// Remember to adjust the allocate size if you change any of the parameter types
		have = ByteBuffer.allocate(9);
		have.put(new byte[]{0,0,0,5,4}); // <length> = 5, <id> = 4
		have.putInt(piece_index); // <piece index>
		return have.array();
	}
	public static byte[] getBitfieldMessage(byte[] bitfield_parameter) {
		// Remember to adjust the allocate size if you change any of the parameter types
		bitfield = ByteBuffer.allocate(5 + bitfield_parameter.length);
		bitfield.putInt(1 + bitfield_parameter.length); // <length> = 5 + bitfield's length
		bitfield.put( (byte) 5); // <id> = 5
		bitfield.put(bitfield_parameter); // <bitfield>
		return bitfield.array();
	}
	public static byte[] getRequestMessage(int index, int begin, int length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		request = ByteBuffer.allocate(17);
		request.put(new byte[]{0,0,0,13,6}); // <length = 13>, <id> = 6
		request.putInt(index); // <index>
		request.putInt(begin); // <begin>
		request.putInt(length); // <length>
		return request.array();
	}
	// Not done yet
	public static byte[] getPieceMessage(int index, int begin, int block_length, byte[] block) {
		// Remember to adjust the allocate size if you change any of the parameter types
		piece = ByteBuffer.allocate(13 + block_length);
		piece.putInt(9 + block_length); // <length> = 9 + block_length
		piece.put((byte)7); // <id> = 7
		piece.putInt(index); // <index>
		piece.putInt(begin); // <begin>
		piece.put(block);
		return piece.array();
	}
	// Not done yet
	public byte[] getCancelMessage(int index, int begin, int length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		cancel = ByteBuffer.allocate(17);
		cancel.put(new byte[]{0,0,0,13,8}); // <length> = 13, <id> = 8
		cancel.putInt(index); // <index>
		cancel.putInt(begin); // <begin>
		cancel.putInt(length); // <length>
		return cancel.array();
	}
	
	/*
	// Not done yet
	public byte[] getPortMessage(byte x, byte index, byte begin, byte length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.port = ByteBuffer.allocate(1);
		this.port.put(new byte[]{0,0,0,3,9});
		return this.port.array();
	}*/
	
	/*
	public static void main(String[] args) {
		MessageLibrary ml = new MessageLibrary();
		
		System.out.println(ml.getBytesAsHex(ml.keep_alive));
		System.out.println(ml.getBytesAsHex(ml.choke));
		System.out.println(ml.getBytesAsHex(ml.unchoke));
		System.out.println(ml.getBytesAsHex(ml.interested));
		System.out.println(ml.getBytesAsHex(ml.not_interested));
	}
	*/

	private static String getBytesAsHex(byte[] bytes) {
		String output = new String();
		for ( byte b : bytes ) {
			output += Integer.toHexString( b & 0xff ) + " " ;
		}
		return output;
	}

}
