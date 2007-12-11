import java.util.ArrayList;


public class Piece {
	ArrayList<BlockRequest> blocks;
	int pieceNum;
	
	public Piece(int pieceNum) {
		blocks = new ArrayList<BlockRequest>();
		this.pieceNum = pieceNum;
	}
	
	public BlockRequest coalesceBlock(BlockRequest br) {
		if (br.prev != null && br.prev.status == BlockRequest.UNASSIGNED) {
			br.prev.length += br.length;
			br.prev.next = br.next;
			blocks.remove(br);
			return br.prev;
		}
		else if (br.next != null && br.next.status == BlockRequest.UNASSIGNED) {
			br.length += br.next.length;
			br.next = br.next.next;
			blocks.remove(br.next);
			return br;
		}
		return null;
	}
	
	public BlockRequest splitBlock() {
		//soo
		return null;
	}
	
	public BlockRequest addBlock(int offset, int length) {
		BlockRequest br = new BlockRequest(pieceNum, offset, length);
		blocks.add(br);
		return br;
	}
	
	public BlockRequest addBlock(int offset, int length, BlockRequest a, BlockRequest b) {
		BlockRequest br = new BlockRequest(pieceNum, offset, length, a, b);
		blocks.add(br);
		return br;
	}

	public BlockRequest getBlock(int offset) {
		for (int i=0; i < blocks.size(); i++) {
			if (blocks.get(i).offset == offset) {
				return blocks.get(i);
			}
		}
		return null;
	}
	
	public void resetAll() {
		for (int i=0; i<blocks.size(); i++) {
			blocks.get(i).status = BlockRequest.UNASSIGNED;
			blocks.get(i).bytesRead = 0;
		}
	}
	
	public boolean allFinished() {
		for (int i=0; i<blocks.size(); i++) {
			if (blocks.get(i).status != BlockRequest.FINISHED) {
				return false;
			}
		}
		return true;
	}
	
	public boolean inProgress()
	{
		if(this.allFinished())
			return false;
		for(int i=0; i<blocks.size(); i++)
		{
			if(blocks.get(i).status == BlockRequest.STARTED || blocks.get(i).status == BlockRequest.FINISHED)
			{
				return true;
			}
		}
		return false;
	}
	
	public String toString() {
		return "Piece: " + pieceNum + " blocks: " + blocks + "\n";
	}
	
}
