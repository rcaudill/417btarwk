/**
 * Main class file for BitTortoise - java bittorrent protocol implementation
 * Class: CMSC417
 * University of Maryland: College Park
 * Created: November 19, 2007
 * 
 * @author Robert Caudill, Kenny Leftin, Andrew Nichols, William Rall
 *
 * References used:
 * 1. Select-like Multiplexing in Java:
 *		http://www.javaworld.com/javaworld/jw-04-2003/jw-0411-select.html     
 * 
 */

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.*;
import java.nio.channels.*;

public class BitTortoise
{
	/**
	 * Usage: "java bittortoise <torrent_file> [<destination_file> [port]]" 
	 * 
	 * @param args
	 */
	public static void main(String args[])
	{
		// Torrent file, tracker, argument, and other parsed variables:
		int port; // the port we are listening on
		TorrentFile torrentFile; // the object into which the .torrent file is b-decoded
		List<Peer> peerList; // list of Peer objects that we got from the tracker
		int interval; // seconds the client should wait before sending a regular request to the tracker
		int min_interval; // seconds the client must wait before sending a regular request to the tracker
		String tracker_id; // a string to send back on next announcements
		int complete; // number of seeders/peers with the entire file
		int incomplete; // number of leechers/peers providing 0+ parts of the file (but are not seeders)
		RandomAccessFile destinationFile; // The file into which we are writing
		byte[] my_peer_id = new byte[20]; // the peer id that this client is using
		
		// State variables:
		BitSet completedPieces; // Whether the Pieces/blocks of the file are completed or not 
		int totalPieceCount = 0;
		Map<SocketChannel, Peer> peerMap;
		
		// Generate a peer_id:
		Random randomGenerator = new Random();
		randomGenerator.nextBytes(my_peer_id);
		
		// Verify that the correct argument(s) were used:
		if(args.length < 1 || args.length > 3)
		{
			System.err.println("Usage: java bittortoise <torrent_file> [<destination_file> [port]]");
			System.exit(1);
		}
		port = 6881; // default port is 6881
		if(args.length == 3)
			port = Integer.parseInt(args[2]);
		
		// Parse the torrent file.
		torrentFile = new TorrentFile();
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
		
		totalPieceCount = ((int)torrentFile.file_length/torrentFile.piece_length) + 1;
		completedPieces = new BitSet(totalPieceCount);
		
		// Extract a list of peers, and other information from the tracker:
		peerList = new LinkedList<Peer>(); // List of peer objects (uses Generics)
		interval = 0; // seconds the client should wait before sending a regular request to the tracker
		min_interval = 0; // seconds the client must wait before sending a regular request to the tracker
		tracker_id = ""; // a string to send back on next announcements
		complete = 0; // number of seeders/peers with the entire file
		incomplete = 0; // number of leechers/peers providing 0+ parts of the file (but are not seeders)
		try
		{
			// Using the parsed torrent file, ping the tracker and get a list of peers to connect to:
			HttpURLConnection connection = (HttpURLConnection)(new URL(torrentFile.tracker_url + "?" +
					"info_hash=" + torrentFile.info_hash_as_url + "&" +
					"downloaded=0" + "&" +
					"uploaded=0" + "&" +
					"left=" + torrentFile.file_length + "&" +
					"peer_id=" + TorrentFileHandler.byteArrayToURLString(my_peer_id) + "&" +
					"port=" + port).openConnection());
			connection.setRequestMethod("GET");
			connection.connect();
			
			// get's the reply from the tracker
			InputStream in = connection.getInputStream();
			
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
										peerList.add(new Peer(torrentFile.info_hash_as_binary, (byte[])peerInformation.get("peer id"), my_peer_id, new String((byte[])(peerInformation.get("ip"))), (Integer)peerInformation.get("port")));
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
			
			// i did this in my example, i'm going to check if i need to keep these open. -andrew
			//in.close();
			//connection.disconnect();
		}
		catch (UnknownHostException e)
		{
			System.err.println("Tracker is an unknown host: " + e.getMessage());
		}
		catch (IOException e) 
		{
			System.err.println("Error connecting to or reading from Tracker: " + e.getMessage());
		}
		
		// Create the destination file:
		try
		{
			if(args.length > 1)
			{
				// If we were given a file name, use it:
				destinationFile = new RandomAccessFile(args[1], "rw");
			}
			else
			{
				// If we were not given a file name, use the string preceding ".torrent" in the torrent file:
				// Ex. "testTorrentFile.txt.torrent" -> "testTorrentFile.txt"
				destinationFile = new RandomAccessFile(args[0].substring(0,args[0].lastIndexOf(".torrent")), "rw");
			}
			// Set the file to the total length of the file:
			destinationFile.setLength(torrentFile.file_length);
		}
		catch(IOException e)
		{
			System.err.println("Error creating file: " + e.getMessage());
			System.exit(1);
		}
		/*
		// Code Sample for writing to a certain area of a file:
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
		*/
		
		// Start the main loop of the client - choose and connect to peers, accept connections from peers, attempt to get all of the file
		try
		{
			// Create the selector:
			Selector select = Selector.open();
			
			// Create the server channel, set it to non-blocking mode
			ServerSocketChannel serverChannel = ServerSocketChannel.open();
			serverChannel.configureBlocking(false);
			
			// Bind the socket represented by the server channel to a port:
			serverChannel.socket().bind(new InetSocketAddress(port));
			
			// Register this server channel within the selector:
			serverChannel.register(select, SelectionKey.OP_ACCEPT);
			
			// Main Data processing loop:
			while(true)
			{
				int num = select.selectNow(); // equivalent: int num = select.select(0);
				
				if(num > 0)
				{
					for(SelectionKey key : select.selectedKeys())
					{
						if(key.isAcceptable())
						{
							// Incoming Connection to the server channel/socket:
							// Accept the connection, set it to not block:
							SocketChannel newConnection = serverChannel.accept();
							newConnection.configureBlocking(false);
							
							// Register the connection with the selector
							newConnection.register(select, SelectionKey.OP_READ & SelectionKey.OP_WRITE);
						}
						else if(key.isReadable())
						{
							// read, process inputs
							ByteBuffer b = ByteBuffer.allocate(1024);
							SocketChannel sc = (SocketChannel)key.channel();
							int size = sc.read(b);
							
							// Get the peer object associated with the connection, if any:
							Peer peer;
							
							if(size == 4)
							{
								// Keep alive Message Received:
							}
							else if(isHandshakeMessage(b))
							{
								// Handshake Message Received:
								byte[] external_peer_id;
							}
							else if(size > 4)
							{
								int length = b.getInt(0); 
								
								byte id = b.array()[4];
								if(id == 0)
								{
									// Choke Message Received:
								}
								else if(id == 1)
								{
									// Un-choke Message Received:
								}
								else if(id == 2)
								{
									// Interested Message Received:
								}
								else if(id == 3)
								{
									// Not Interested Message Received:
								}
								else if(id == 4)
								{
									// Have Message Received:
								}
								else if(id == 5)
								{
									// Bitfield Message Received:
								}
								else if(id == 6)
								{
									// Request Message Received:
								}
								else if(id == 7)
								{
									// Piece Message Received:
								}
								else if(id == 8)
								{
									// Cancel Message Received:
								}
								else if(id == 9)
								{
									// Port Message Received:
								}
								else
								{
									// Unrecognized id
								}
							}
							else
							{
								// Message too small - ignore?
							}
						}
						else if(key.isWritable())
						{
							// if this is a peer we are writing to, that's 
						}
					}
				}
				
				// Check the number of connections, add more if needed
			}
		}
		catch(Exception e)
		{
			System.err.println("Error Occurred!");
			System.exit(1);
		}
		
		System.out.println("Success!");
	}
	
	/**
	 * Create a byte array from a bit set: used for the bitfield message in the BitTorrent Protocol 
	 * 
	 * @param bs BitSet from which we want to create a byte array
	 * @param numBits The number of bits that we are dealing with
	 */
	public static byte[] byteArrayFromBitSet(BitSet bs, int numBits)
	{
		byte[] bytes = new byte[(numBits / 8) + ((numBits % 8 == 0)? 0 : 1)];
		
		for(int i = 0; i < numBits; i ++)
		{
			if(bs.get(i))
			{
				bytes[i/8] |= (byte)((1 << (7 - i % 8)));
			}
		}
		
		return bytes;
	}
	
	/**
	 * Create a byte array from a bit set: used for the bitfield message in the BitTorrent Protocol 
	 * 
	 * @param buf the buffer that we are checking against
	 */
	public static boolean isHandshakeMessage(ByteBuffer buf)
	{
		// 49 + pstrlen (19) = 68
		if(buf.remaining() < 68)
		{
			buf.position(0);
			return false;
		}
		if(buf.getChar() != (char)19)
		{
			buf.position(0);
			return false;
		}
		
		byte[] bytes = new byte[19];
		buf.get(bytes, 1, 19);
		
		if(!(("BitTorrent protocol").equalsIgnoreCase(new String(bytes))))
		{
			buf.position(0);
			return false;
		}
		
		buf.position(0);
		
		return true;
	}
}
