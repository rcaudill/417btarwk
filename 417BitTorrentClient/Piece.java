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
	
	public BlockRequest getBlock() {
		return blocks.get(0);
	}
	
	public void removeBlock(BlockRequest br) {
		blocks.remove(br);
	}
	
	public String toString() {
		return "Piece: " + pieceNum + " blocks: " + blocks + "\n";
	}
	
}
