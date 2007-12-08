import java.util.ArrayList;


public class Piece {
	ArrayList<BlockRequest> blocks;
	int pieceNum;
	
	public Piece(int pieceNum) {
		blocks = new ArrayList<BlockRequest>();
		this.pieceNum = pieceNum;
	}
	
	public void addBlock(int offset, int length) {
		blocks.add(new BlockRequest(pieceNum, offset, length));
	}
	
	public BlockRequest getBlock(int offset) {
		for (int i=0; i < blocks.size(); i++) {
			if (blocks.get(i).offset == offset) {
				return blocks.get(i);
			}
		}
		return null;
	}
	
	public void removeBlock(BlockRequest br) {
		blocks.remove(br);
	}
	
	public boolean allFinished() {
		for (int i=0; i<blocks.size(); i++) {
			if (blocks.get(i).status != BlockRequest.FINISHED) {
				return false;
			}
		}
		return true;
	}
	
	public String toString() {
		return "Piece: " + pieceNum + " blocks: " + blocks + "\n";
	}
	
}
