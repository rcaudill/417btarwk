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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

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
			
			// Using the parsed torrent file, ping the tracker and get a list of peers to connect to:
			//port is hardcoded for now, but it can be an arg if we need it to be.
			HttpURLConnection connection = (HttpURLConnection)(new URL(torrentFile.tracker_url+"?"+
					"info_hash="+torrentFile.info_hash_as_url+"&"+
					"downloaded=0"+"&"+
					"uploaded=0"+"&"+
					"left="+torrentFile.file_length+"&"+
					"peer_id="+torrentFile.info_hash_as_url+"&"+
					"port="+"6881").openConnection());
			connection.setRequestMethod("GET");
			connection.connect();
			
			/*get's the reply from the tracker*/
			InputStream in = connection.getInputStream();
			
			/*response comes in the form
			 * hashmap: different entries
			 * peers: linked list of hashmaps*/
			
			HashMap response = (HashMap)Bencoder.bdecode(in);
			LinkedList peerList = (LinkedList)response.get("peers");
			HashMap[] peerMap = new HashMap[peerList.size()];
			
			for(int i=0;i<peerList.size();i++){
				peerMap[i] = (HashMap)peerList.get(i);
			}
			
			/*create new peer objects from response
			 * decode some values*/
			
			/*peer keys: id, port, ip
			 * get byte[]s back*/
			byte[] daPeerId = (byte[])peerMap[0].get("peer id");
			byte[] daIp = (byte[])peerMap[0].get("ip");
			//the peerId doesn't translate well, maybe someone else knows why...
			System.out.println(new String(daPeerId));
			System.out.println(new String(daIp));
			/*i did this in my example, i'm going to check if i need to keep these open. -andrew*/
			//in.close();
			//connection.disconnect();
		}catch (UnknownHostException e)
		{
			System.err.println(e);
		}catch (IOException e) 
		{
			System.err.println(e);
		}catch(Exception e)
		{
			System.out.println("The provided file was not of the appropriate format, or could not be read.");
			System.exit(1);
		}

		//peerList = decode(trackerResponse).getPeers
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
