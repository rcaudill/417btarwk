
public class BlockRequest {
	int piece;
	int offset;
	int length;
	boolean requestSent;
	
	public BlockRequest(int piece, int offset, int length) {
		this.piece = piece;
		this.offset = offset;
		this.length = length;
		requestSent = false;
	}
	
	public String toString() {
		return "Block: " + piece + " offset: " + offset;
	}
	//void sendRequest(Peer p);
	
}

