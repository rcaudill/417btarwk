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
		int numConnections; // the number of TCP connections we currently have with other peers
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
		BitSet alreadyRequested; // Pieces that have been requested inside a peer.
		int totalPieceCount = 0;
		Map<SocketChannel, Peer> peerMap = new HashMap<SocketChannel, Peer>();
		
		// Generate a peer_id:
		my_peer_id[0] = (byte)'-'; // Replace the beginning of the id with "-BT0001-" to mimic normal naming schemes 
		my_peer_id[1] = (byte)'B';
		my_peer_id[2] = (byte)'T';
		my_peer_id[3] = (byte)'0';
		my_peer_id[4] = (byte)'0';
		my_peer_id[5] = (byte)'0';
		my_peer_id[6] = (byte)'1';
		my_peer_id[7] = (byte)'-';
		for(int i = 8; i < my_peer_id.length; i ++)
			my_peer_id[i] = (byte)((Math.random() * 0x5F) + 0x20); // make sure these are printable characters (range from 0x20 to 0x7E)
		
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
		byte[] buffer = new byte[1000];
		ByteBuffer byteBuffer = ByteBuffer.allocate(1000);
		
		//new Peer(torrentFile.info_hash_as_binary, (byte[])peerInformation.get("peer id"), my_peer_id, new String((byte[])(peerInformation.get("ip"))), (Integer)peerInformation.get("port")))
		for (int i=0; i < peerList.size(); i++) {
			//System.out.println(peerList.get(i));
			Peer peer = peerList.get(i);
			Socket socket;
			try 
			{ 
				socket = new Socket(peer.ip, peer.port); 
				socket.getOutputStream().write(peer.handshake);
				int numRead = socket.getInputStream().read(buffer);
				System.out.println("Successful connect to " + peer.ip + ":" + peer.port);
				//peer.processMessage(buffer, numRead);
				System.out.println(buffer);
				System.out.println("received " + numRead + " bytes: " +  Peer.getBytesAsHex(buffer));
				//System.out.println("Message Type:" + peer.theType);
			} catch (Exception e) {
				System.out.println("Couldn't connect to " + peer.ip + ":" + peer.port);
				System.out.println("removing peer from peerlist");
				peerList.remove(peer);
			}

		}*/
		
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
		alreadyRequested = new BitSet();
		numConnections = 0;
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
				int num = select.select(0);
				
				if(num > 0)
				{
					for(SelectionKey key : select.selectedKeys())
					{
						try
						{
							if(key.isAcceptable())
							{
								// This must be from the server socket.
								// Only accept new connections if we have less than a desirable number:
								if(numConnections <= 56)
								{
									// Incoming Connection to the server channel/socket:
									// Accept the connection, set it to not block:
									SocketChannel newConnection = serverChannel.accept();
									newConnection.configureBlocking(false);
									
									// Register the connection with the selector
									newConnection.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
									
									numConnections ++;
								}
							}
							else if(key.isConnectable())
							{
								SocketChannel sc = (SocketChannel)key.channel();
								Peer p = peerMap.get(sc);
								
								if(sc.finishConnect())
								{
									try
									{
										sc.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
										
										// Send handshake message to the peer:
										sc.write(ByteBuffer.wrap(p.handshake));

										// Update situation:
										p.handshake_sent = true;
										
										numConnections ++;
									}
									catch(IOException e)
									{
										System.err.println("Could not open new connection to peer - " + e.getMessage());
										
										if(peerMap.containsValue(p))
										{
											removePeer(p, peerMap);
										}
									}
								}
								else
								{
									System.err.println("Could not open new connection to peer.");
									
									if(peerMap.containsValue(p))
									{
										removePeer(p, peerMap);
									}
								}
							}
							else if(key.isReadable())
							{
								SocketChannel sc = (SocketChannel)key.channel();
								// read, process inputs
								// Check if this SocketChannel is already mapping to a peer - if not, we can only accept a handshake from it - if so, we are cool
								int size = -1;
								if(peerMap.containsKey(sc))
								{
									if(!peerMap.get(sc).readAndProcess(sc, true))
									{
										key.cancel();
										peerMap.remove(sc);
										try
										{
											if(!sc.socket().isClosed())
												sc.socket().close();
										}
										catch(IOException e)
										{
											System.err.println("Error closing socket!");
										}
									}
								}
								else
								{
									ByteBuffer buf = ByteBuffer.allocate(1024);
									size = sc.read(buf);
									
									if(size > 67 && isHandshakeMessage(buf))
									{
										// Handshake Message Received:
										byte[] external_info_hash = new byte[20];
										byte[] external_peer_id = new byte[20];
										
										buf.position(28);
										buf.get(external_info_hash, 0, 20);
										
										buf.position(48);
										buf.get(external_peer_id, 0, 20);
										
										buf.position(68);
										buf.compact();
										buf.position(0);
										size -= 68;
										
										// Check if the info hash that was given matches the one we are providing (by wrapping in a ByteBuffer, using .equals)
										if(!ByteBuffer.wrap(external_info_hash).equals(ByteBuffer.wrap(torrentFile.info_hash_as_binary)))
										{
											// Peer requested connection for a bad info hash - Throw out connection ?
										}
										else
										{
											Peer connectedTo = new Peer(torrentFile.info_hash_as_binary, external_peer_id, my_peer_id, sc.socket().getInetAddress().getHostAddress(), sc.socket().getPort());
											
											if(!peerMap.containsValue(connectedTo))
											{
												peerMap.put(sc, connectedTo);
											}
											else
											{
												// We have already added this connection to the map - ignore ?
											}
											connectedTo.handshake_received = true;
											
											connectedTo.bytesLeft = size;
											connectedTo.readBuffer = buf;
										}
										
										if(size != 0)
										{
											if(peerMap.get(sc).readAndProcess(sc, false))
											{
												key.cancel();
												peerMap.remove(sc);
												try
												{
													sc.socket().close();
												}
												catch(IOException e)
												{
													System.err.println("Error closing socket!");
												}
											}
										}
									}
									else
									{
										// ignore messages that are sent before a handshake, or short handshakes...
										continue;
									}
								}
							}
							else if(key.isWritable())
							{
								// Get the peer 
								SocketChannel sc = (SocketChannel)key.channel();
								Peer writablePeer = peerMap.get(sc);
								writablePeer.sendMessage(sc, alreadyRequested, completedPieces);
							}
							else
								System.out.println("other");
						}
						catch(IOException e)
						{
							System.err.println("IO error - " + e.getMessage());
							key.cancel();
						}
					}
				}
				
				// Clear the list:
				select.selectedKeys().clear();
				
				// Check the number of connections, add more if needed
				if(numConnections < 30 && peerList.size() > 0)
				{
					boolean succeeded = false;
					while(!succeeded)
					{
						int last = peerList.size() - 1;
						if(last >= 0)
						{
							Peer toConnect = peerList.get(last);
							
							if(!peerMap.containsValue(toConnect))
							{
								// Send handshake to peer:
								try
								{
									// Open a new connection to the peer, set to not block:
									SocketChannel sc = SocketChannel.open();
									sc.configureBlocking(false);
									sc.connect(new InetSocketAddress(toConnect.ip, toConnect.port));
									
									// Register the new connection with the selector:
									sc.register(select, SelectionKey.OP_CONNECT);
									
									// Add the new peer to the Map:
									peerMap.put(sc, toConnect);
									
									succeeded = true;
								}
								catch(IOException e)
								{
									System.err.println("Could not open new connection to peer - " + e.getMessage());
									
									if(peerMap.containsValue(toConnect))
									{
										removePeer(toConnect, peerMap);
									}
									
									peerList.remove(last);
								}
								peerList.remove(last);
							}
							else
							{
								// Remove from the list.
								peerList.remove(last);
							}
						}
					}
				}
				else if(numConnections < 30)
				{
					// Query the tracker again to get more peers to connect to.
				}
			}
		}
		catch(IOException e)
		{
			System.err.println("IOException Occurred! - " + e.getMessage());
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
	 * Create a BitSet from a byte array: used for handling the bitfield message in the BitTorrent Protocol 
	 * 
	 * @param ba byte array from which we want to create a BitSet
	 */
	public static BitSet bitSetFromByteArray(byte[] ba)
	{
		BitSet a = new BitSet(ba.length * 8);
		
		for(int i = 0; i < ba.length; i ++)
		{
			if(ba[i] != 0)
			{
				int temp = i*8;
				a.set(temp, (ba[i] & 0x80) == 0x80);
				a.set(temp + 1, (ba[i] & 0x40) == 0x40);
				a.set(temp + 2, (ba[i] & 0x20) == 0x20);
				a.set(temp + 3, (ba[i] & 0x10) == 0x10);
				a.set(temp + 4, (ba[i] & 0x08) == 0x08);
				a.set(temp + 5, (ba[i] & 0x04) == 0x04);
				a.set(temp + 6, (ba[i] & 0x02) == 0x02);
				a.set(temp + 7, (ba[i] & 0x01) == 0x01);
			}
		}
		
		return a;
	}
	
	/**
	 * Create a byte array from a bit set: used for making the bitfield message in the BitTorrent Protocol 
	 * 
	 * @param buf the buffer that we are checking against
	 */
	public static boolean isHandshakeMessage(ByteBuffer buf)
	{
		buf.position(0);
		byte b = buf.get();
		if(b != (byte)19)
		{
			buf.position(0);
			return false;
		}
		
		buf.position(1);
		if(buf.remaining() > 19)
		{
			byte[] bytes = new byte[19];
			buf.get(bytes, 0, 19);
			
			String test = new String("BitTorrent protocol");
			String bytesString = new String(bytes);
			
			if(!test.equalsIgnoreCase(bytesString))
			{
				buf.position(0);
				return false;
			}
		}
		
		buf.position(0);
		return true;
	}
	
	/**
	 * Remove the peer p from the map m 
	 * 
	 * @param p the Peer that we are removing from the map
	 * @param m the Map from which we are removing the peer
	 */
	public static void removePeer(Peer p, Map<SocketChannel, Peer> m)
	{
		for(Map.Entry<SocketChannel, Peer> e : m.entrySet())
		{
			if(e.getValue().equals(p)){
				m.remove(e.getKey());
				return;
			}
		}
	}
}
