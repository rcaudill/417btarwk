import java.util.Comparator;


public class rarityComparator implements Comparator<Piece> {

	public int compare(Piece p1, Piece p2) {
		//if rarity is 0, move it to the end of the list
		if(p1.commonality == 0 || p2.commonality == 0){
			if(p1.commonality == 0){
				return 1;
			}else
				return -1;
		}
		
		//the more common the piece the less rare it is
		if(p1.commonality > p2.commonality){
			return 1;
		}else if(p1.commonality < p2.commonality){
			return -1;
		}else
			return 0;
	}

}
