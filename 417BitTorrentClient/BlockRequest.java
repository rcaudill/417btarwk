import java.util.*;
import java.util.regex.*;

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
	
	int piece; // This should always be non-negative
	int offset; // This should always be non-negative
	int length; // This should always be non-negative
	int status; //0 is empty, 1 is started, 2 is finished
	int bytesRead; // This should always be non-negative
	
	private BlockRequest()
	{
		this.piece = -1;
		this.offset = -1;
		this.length = -1;
		bytesRead = 0;
		status = UNASSIGNED;
		this.prev = null;
		this.next = null;
		this.timeModified = (new Date()).getTime();
	}
	
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
	
	public String toString()
	{
		return "Piece: " + piece + " Offset: " + offset + " Length: " + length + " Status: " + status;
	}
	//void sendRequest(Peer p);
	
	/**
	 * Used to write status to a file (in Resumer.java).
	 * @return String representing the BlockRequest's piece, offset, length, and status.
	 */
	public String toPrintString()
	{
		return "Piece: " + piece + " Offset: " + offset + " Length: " + length + " Status: " + status;
	}
	
	/**
	 * Used to read BlockRequests in from a file (in Resumer.java), uses Regular Expressions.
	 * @param s String to convert into a BlockRequest
	 * @return BlockRequest representing the BlockRequest's piece, offset, length, and status.
	 */
	public static BlockRequest fromString(String s)
	{ 
		BlockRequest result = new BlockRequest();
		String regex = "Piece: (-?[0-9]+) Offset: (-?[0-9]+) Length: (-?[0-9]+) Status: (-?[0-9])";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(s);
		if(m.matches())
		{
			result.piece = Integer.parseInt(m.group(1));
			result.offset = Integer.parseInt(m.group(2));
			result.length = Integer.parseInt(m.group(3));
			result.status = Integer.parseInt(m.group(4));
			return result;
		}
		else
		{
			return null;
		}
	}
}

