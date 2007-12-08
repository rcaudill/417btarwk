import java.util.Comparator;


public class topThreeComparator implements Comparator<Peer> {

	public int compare(Peer p1, Peer p2) {
		if(p1.bytesReadThisRound > p2.bytesReadThisRound){
			return 1;
		}else if(p1.bytesReadThisRound > p2.bytesReadThisRound){
			return -1;
		}else{
			return 0;
		}
	}

}
