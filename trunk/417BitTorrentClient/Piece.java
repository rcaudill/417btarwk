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
	
}
