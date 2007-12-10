import java.util.Comparator;


public class topThreeComparator implements Comparator<Peer>
{
	/**
	 * Compares p1 to p2
	 * @param p1 the first peer
	 * @param p2 the second peer
	 * @return -1 iff p1 < p2, 0 iff p1 == p2, 1 if p1 > p2
	 */
	public int compare(Peer p1, Peer p2)
	{
		// Note, we want the highest bytesReadThisRound to be at the lowest end of the array (0)
		if(p1.bytesReadThisRound < p2.bytesReadThisRound)
		{
			return 1;
		}
		else if(p1.bytesReadThisRound > p2.bytesReadThisRound)
		{
			return -1;
		}
		else
		{
			if(!p1.am_choking && p2.am_choking)
			{
				return -1;
			}
			else if(p1.am_choking && !p2.am_choking)
			{
				return 1;
			}
			else
			{
				return 0;
			}
		}
	}

}
