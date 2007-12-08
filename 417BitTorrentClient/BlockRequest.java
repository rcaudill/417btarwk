public class BlockRequest {
	public static final int EMPTY = 0;
	public static final int STARTED = 1;
	public static final int FINISHED = 2;
	int piece;
	int offset;
	int length;
	int status; //0 is empty, 1 is started, 2 is finished
	int bytesRead;
	
	public BlockRequest(int piece, int offset, int length) {
		this.piece = piece;
		this.offset = offset;
		this.length = length;
		bytesRead = 0;
		status = EMPTY;
	}
	
	public String toString() {
		return "Block: " + piece + " offset: " + offset;
	}
	//void sendRequest(Peer p);
	
}

