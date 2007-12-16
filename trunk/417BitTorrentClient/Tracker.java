import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;


public class Tracker {
	// Extract a list of peers, and other information from the tracker:
	public LinkedList<Peer> peerList; // List of peer objects (uses Generics)
	public int interval; // seconds the client should wait before sending a regular request to the tracker
	public int min_interval; // seconds the client must wait before sending a regular request to the tracker
	public String tracker_id; // a string to send back on next announcements
	public int complete; // number of seeders/peers with the entire file
	public int incomplete; // number of leechers/peers providing 0+ parts of the file (but are not seeders)
	HttpURLConnection connect;
	TorrentFile torrentFile;
	byte[] peerid;
	String key;
	
	public Tracker(TorrentFile torrentFile){
		peerList = new LinkedList<Peer>();
		interval = 5;
		min_interval = 5;
		tracker_id = null;
		complete = 0;
		incomplete = 0;
		this.torrentFile = torrentFile;
		this.key = ""; 
	}
	
	/**
	 * Use this connect any time after the first call to connect(arg1,arg2);
	 */
	public void connect(){
		try{
			connect(connect,peerid);
		}catch(Exception e){
			System.err.println("Use other connect(arg1,arg2) before using parameterless one");
		}
	}
	/**
	 * Use this connect the first time
	 * 
	 * @param sentTrackerURL - the address (variables and all) to be sent to the tracker
	 * @param sentPeerid - bittortoise's peerid
	 */
	public void connect(HttpURLConnection sentTrackerURL, byte[] sentPeerid)
	{
		connect(sentTrackerURL, sentPeerid, true);
	}
	
	public void connect(HttpURLConnection sentTrackerURL, byte[] sentPeerid, boolean decodeResponse){
		connect = sentTrackerURL;
		
		String local_ip = "127.0.0.1";
		
		try
		{
			byte[] local_ip_bytes = InetAddress.getLocalHost().getAddress();
			local_ip = ipFromBytes(local_ip_bytes[0], local_ip_bytes[1], local_ip_bytes[2], local_ip_bytes[3]);
		}
		catch(UnknownHostException e) { }
		
		this.peerid = sentPeerid;
		try
		{
			connect.setRequestMethod("GET");
			connect.connect();
			
			// get's the reply from the tracker
			InputStream in = connect.getInputStream();
			
			if(decodeResponse)
			{
				// Decode the returned message, translate it into peer objects and such.
				Object response = Bencoder.bdecode(in);
				if(response instanceof Map)
				{
					Map responseMap = (Map)response;
					
					if(responseMap.containsKey("failure reason"))
					{
						System.out.println("Tracker reported the following failure: " + responseMap.get("failure reason"));
						System.exit(1);
					}
					else
					{
						if(responseMap.containsKey("peers"))
						{
							Object p = responseMap.get("peers");
							
							if(p instanceof List)
							{
								List peers = (List)p;
								
								for(Object o : peers)
								{
									if(o instanceof Map)
									{
										Map peerInformation = (Map)o;
										
										if(peerInformation.containsKey("peer id") && peerInformation.containsKey("port") && peerInformation.containsKey("ip"))
										{
											if(!java.util.Arrays.equals((byte[])peerInformation.get("peer id"),peerid) && !(new String((byte[])(peerInformation.get("ip")))).equals(local_ip))
												peerList.add(new Peer(torrentFile.info_hash_as_binary, (byte[])peerInformation.get("peer id"), peerid, new String((byte[])(peerInformation.get("ip"))), (Integer)peerInformation.get("port")));
										}
										else
										{
											System.out.println("Tracker gave a bad peer response.  Skipping...");
										}
									}
									else
									{
										System.out.println("Tracker gave a bad peer response.  Skipping...");
									}
								}
							}
							else if(p instanceof byte[])
							{
								byte[] array = (byte[])p;
								
								for(int i=0; i<array.length; i+=6)
								{
									String ip = ipFromBytes(array[i+0],array[i+1],array[i+2],array[i+3]);
									if(!ip.equals(local_ip))
									{
										int port1 = array[i+4] & 0xff;
										int port2 = array[i+5] & 0xff;
										int port = port1 * 256 + port2;
										peerList.add(new Peer(torrentFile.info_hash_as_binary, new byte[20], peerid, ip, port));
									}
								}
							}
							else
							{
								System.out.println("Tracker returned no peers.");
								System.exit(1);
							}
						}
						else
						{
							System.out.println("Tracker returned no peers.");
							System.exit(1);
						}
						
						if(responseMap.containsKey("interval"))
						{
							interval = (Integer)responseMap.get("interval");
						}
						
						if(responseMap.containsKey("min interval"))
						{
							min_interval = (Integer)responseMap.get("min interval");
						}
						
						if(responseMap.containsKey("tracker id"))
						{
							tracker_id = new String((byte[])responseMap.get("tracker id"));
						}
						
						if(responseMap.containsKey("complete"))
						{
							complete = (Integer)responseMap.get("complete");
						}
						
						if(responseMap.containsKey("incomplete"))
						{
							incomplete = (Integer)responseMap.get("incomplete");
						}
						
						if(responseMap.containsKey("warning message"))
						{
							System.out.println("Tracker Warning: " + new String((byte[])responseMap.get("warning message")));
						}
					}
				}
				else
				{
					System.out.println("Tracker returned an unexpected type.");
					System.exit(1);
				}
				in.close();
			}
			connect.disconnect();
		}
		catch (ProtocolException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": " + e.getMessage());
		}
		catch (IOException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": " + e.getMessage());
		}
	}
	
	public void alertCompleted(long totalDown, long totalUp, byte[] peer_id, int port)
	{
		try
		{
			HttpURLConnection tempConnection = (HttpURLConnection)(new URL(torrentFile.tracker_url + "?" + 
					"info_hash=" + torrentFile.info_hash_as_url + "&" + 
					"peer_id=" + TorrentFileHandler.byteArrayToURLString(peer_id) + "&" + 
					"port=" + port + "&" + 
					"uploaded=" + totalUp + "&" + 
					"downloaded=" + totalDown + "&" + 
					"left=0" + "&" + 
					"key=" + this.key + "&" + 
					"event=completed" + "&" + 
					"numwant=0" + "&" + 
					"compact=1" + "&" + 
					((this.tracker_id == null)? ("") : ("tracker_id=" + this.tracker_id + "&")) + 
					"no_peer_id=1").openConnection());
			
			this.connect(tempConnection, peer_id, false);
		}
		catch(IOException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Unable to alert tracker that this download has completed.");
		}
	}
	
	public void alertStopped(long totalDown, long totalUp, byte[] peer_id, int port)
	{
		try
		{
			HttpURLConnection tempConnection = (HttpURLConnection)(new URL(torrentFile.tracker_url + "?" + 
					"info_hash=" + torrentFile.info_hash_as_url + "&" + 
					"peer_id=" + TorrentFileHandler.byteArrayToURLString(peer_id) + "&" + 
					"port=" + port + "&" + 
					"uploaded=" + totalUp + "&" + 
					"downloaded=" + totalDown + "&" + 
					"left=0" + "&" + 
					"key=" + this.key + "&" + 
					"event=stopped" + "&" + 
					"numwant=0" + "&" + 
					"compact=1" + "&" + 
					((this.tracker_id == null)? ("") : ("trackerid=" + this.tracker_id + "&")) + 
					"no_peer_id=1").openConnection());
			
			this.connect(tempConnection, peer_id, false);
		}
		catch(IOException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Unable to alert tracker that this download has stopped.");
		}
	}
	
	public String ipFromBytes(byte one, byte two, byte three, byte four)
	{
		String part1 = "";
		if(one < 0)
			part1 += (one & 0xff);
		else
			part1 += one;
		
		String part2 = "";
		if(two < 0)
			part2 += (two & 0xff);
		else
			part2 += two;
		
		String part3 = "";
		if(three < 0)
			part3 += (three & 0xff);
		else
			part3 += three;
		
		String part4 = "";
		if(four < 0)
			part4 += (four & 0xff);
		else
			part4 += four;
		
		return (part1 + "." + part2 + "." + part3 + "." + part4);
	}
}
