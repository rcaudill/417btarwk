import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
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
	
	public Tracker(TorrentFile torrentFile){
		peerList = new LinkedList<Peer>();
		min_interval = 0;
		tracker_id = "";
		complete = 0;
		incomplete = 0;
		this.torrentFile = torrentFile;
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
	public void connect(HttpURLConnection sentTrackerURL, byte[] sentPeerid){
		connect = sentTrackerURL;
		this.peerid = sentPeerid;
		try {
			connect.setRequestMethod("GET");
			connect.connect();

			// get's the reply from the tracker
			InputStream in = connect.getInputStream();

			// Decode the returned message, translate it into peer objects and such.
			Object response = Bencoder.bdecode(in);
			if(response instanceof Map)
			{
				Map responseMap = (Map)response;

				if(responseMap.containsKey("failure reason"))
				{
					System.err.println("Tracker reported the following failure: " + responseMap.get("failure reason"));
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
										peerList.add(new Peer(torrentFile.info_hash_as_binary, (byte[])peerInformation.get("peer id"), peerid, new String((byte[])(peerInformation.get("ip"))), (Integer)peerInformation.get("port")));
									}
									else
									{
										System.err.println("Tracker gave a bad peer response.  Skipping...");
									}
								}
								else
								{
									System.err.println("Tracker gave a bad peer response.  Skipping...");
								}
							}
						}
						else
						{
							System.err.println("Tracker returned no peers.");
							System.exit(1);
						}
					}
					else
					{
						System.err.println("Tracker returned no peers.");
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
						System.err.println("Tracker Warning: " + new String((byte[])responseMap.get("warning message")));
					}
				}
			}
			else
			{
				System.err.println("Tracker returned an unexpected type.");
				System.exit(1);
			}
			in.close();
			connect.disconnect();
		} catch (ProtocolException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
