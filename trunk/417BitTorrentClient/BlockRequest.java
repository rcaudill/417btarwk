
public class BlockRequest {
	int piece;
	int offset;
	int length;
	boolean requestSent;
	boolean isRead;
	int bytesRead;
	
	public BlockRequest(int piece, int offset, int length) {
		this.piece = piece;
		this.offset = offset;
		this.length = length;
		bytesRead = 0;
		isRead = false;
		requestSent = false;
	}
	
	public String toString() {
		return "Block: " + piece + " offset: " + offset;
	}
	//void sendRequest(Peer p);
	
}

