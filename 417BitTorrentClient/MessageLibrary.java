import java.nio.ByteBuffer;

public class MessageLibrary
{

	public final byte[] keep_alive = 		new byte[]{0,0,0,0};
	public final byte[] choke = 			new byte[]{0,0,0,1,0};
	public final byte[] unchoke = 			new byte[]{0,0,0,1,1};
	public final byte[] interested = 		new byte[]{0,0,0,1,2};
	public final byte[] not_interested =	new byte[]{0,0,0,1,3};

	private ByteBuffer have;
	private ByteBuffer bitfield;
	private ByteBuffer request;
	private ByteBuffer piece;
	private ByteBuffer cancel;
//	private ByteBuffer port;
	
	public byte[] getHaveMessage(int piece_index) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.have = ByteBuffer.allocate(9);
		this.have.put(new byte[]{0,0,0,5,4}); // <length> = 5, <id> = 4
		this.have.putInt(piece_index); // <piece index>
		return this.have.array();
	}
	public byte[] getBitfieldMessage(byte[] bitfield_parameter) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.bitfield = ByteBuffer.allocate(5 + bitfield_parameter.length);
		this.bitfield.putInt(1 + bitfield_parameter.length); // <length> = 5 + bitfield's length
		this.bitfield.put( (byte) 5); // <id> = 5
		this.bitfield.put(bitfield_parameter); // <bitfield>
		return this.bitfield.array();
	}
	public byte[] getRequestMessage(int index, int begin, int length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.request = ByteBuffer.allocate(17);
		this.request.put(new byte[]{0,0,0,13,6}); // <length = 13>, <id> = 6
		this.request.putInt(index); // <index>
		this.request.putInt(begin); // <begin>
		this.request.putInt(length); // <length>
		return this.request.array();
	}
	// Not done yet
	public byte[] getPieceMessage(int index, int begin, int block_length, byte[] block) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.piece = ByteBuffer.allocate(13 + block_length);
		this.piece.putInt(9 + block_length); // <length> = 9 + block_length
		this.piece.put((byte)7); // <id> = 7
		this.piece.putInt(index); // <index>
		this.piece.putInt(begin); // <begin>
		this.piece.put(block);
		return this.piece.array();
	}
	// Not done yet
	public byte[] getCancelMessage(int index, int begin, int length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.cancel = ByteBuffer.allocate(17);
		this.cancel.put(new byte[]{0,0,0,13,8}); // <length> = 13, <id> = 8
		this.cancel.putInt(index); // <index>
		this.cancel.putInt(begin); // <begin>
		this.cancel.putInt(length); // <length>
		return this.cancel.array();
	}
	
	/*
	// Not done yet
	public byte[] getPortMessage(byte x, byte index, byte begin, byte length) {
		// Remember to adjust the allocate size if you change any of the parameter types
		this.port = ByteBuffer.allocate(1);
		this.port.put(new byte[]{0,0,0,3,9});
		return this.port.array();
	}*/
	
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
