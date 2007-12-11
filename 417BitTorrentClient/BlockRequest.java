import java.util.*;

public class BlockRequest
{
	public static final int UNASSIGNED = -2; // Incoming Block: we have not yet assigned this request to a peer.  Outgoing Block: unused.
	public static final int UNREQUESTED = -1; // Incoming Block: we have not yet requested this piece yet.  Outgoing Block: This request has been received, but not responded to yet.
	public static final int REQUESTED = 0; // Incoming Block: we have requested, but not received any of it.
	public static final int STARTED = 1; // Incoming Block: we have started receiving it.  Outgoing: unused.
	public static final int FINISHED = 2; // Incoming Block: we have finished receiving it.  Outgoing: unused.
	
	public long timeModified;
	
	BlockRequest next;
	BlockRequest prev;
	
	int piece;
	int offset;
	int length;
	int status; //0 is empty, 1 is started, 2 is finished
	int bytesRead;
	
	
	
	public BlockRequest(int piece, int offset, int length, BlockRequest prev, BlockRequest next) {
		this.piece = piece;
		this.offset = offset;
		this.length = length;
		bytesRead = 0;
		status = UNASSIGNED;
		this.prev = prev;
		this.next = next;
		this.timeModified = (new Date()).getTime();
	}
	
	public BlockRequest(int piece, int offset, int length) {
		this.piece = piece;
		this.offset = offset;
		this.length = length;
		bytesRead = 0;
		status = UNASSIGNED;
	}
	
	public String toString() {
		return "Block: " + piece + " offset: " + offset;
	}
	//void sendRequest(Peer p);
	
}

