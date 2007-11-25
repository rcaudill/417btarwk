/**
 * Main class file for BitTortoise - java bittorrent protocol implementation
 * Class: CMSC417
 * University of Maryland: College Park
 * Created: November 19, 2007
 * 
 * @author Robert Caudill, Kenny Leftin, Andrew Nichols, William Rall
 *
 */

import java.io.*;
import java.util.ArrayList;

public class BitTortoise
{
	/**
	 * Usage: "java bittortoise <torrent_file> [<destination_file>]" 
	 * 
	 * @param args
	 */
	public static void main(String args[])
	{
		// Verify that the correct argument(s) were used:
		if(args.length < 1 || args.length > 2)
			System.out.println("Usage: java bittortoise <torrent_file> [<destination_file>]");
		
		// Parse the torrent file.
		TorrentFile torrentFile;
		// Note: this was put in a try block because this sometimes breaks when reading a bad torrent file
		try
		{
			TorrentFileHandler torrentFileHandler = new TorrentFileHandler();
			torrentFile = torrentFileHandler.openTorrentFile(args[0]);
		}
		catch(Exception e)
		{
			System.out.println("The provided file was not of the appropriate format, or could not be read.");
			System.exit(1);
		}
		
		// Using the parsed torrent file, ping the tracker and get a list of peers to connect to:
		
		//peerList = pingTracker(...)?;
		ArrayList peers = new ArrayList();
		//for each p in peerList {
			//peers.add(new Peer(p.pstr, p.reserved, p.info_hash, p.peer_id));
		//}
		
		try
		{
			RandomAccessFile raf = new RandomAccessFile("tempTestFile.txt","rw");
			byte arr[] = new byte[1024];
			for(int i = 0; i < 1024; i++)
				arr[i] = 0x66;
			raf.setLength(1024);
			raf.seek(1022);
			raf.write(arr, 1022, 2);
		}
		catch(IOException e)
		{
			System.out.println("error occurred.");
		}
		
		// Start the main loop of the client - choose and connect to peers, accept connections from peers, attempt to get all of the file
	}
}
