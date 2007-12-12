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
	public static boolean verbose = false;
	
	public static final int MAX_OUTSTANDING_REQUESTS = 5;
	
	private static TorrentFile torrentFile; // the object into which the .torrent file is b-decoded
	private static RandomAccessFile destinationFile; // The file into which we are writing
	private static Map<Integer, Piece> outstandingPieces = new HashMap<Integer, Piece>();
	private static int block_length = 16384; //The reality is near all clients will now use 2^14 (16KB) requests. Due to clients that enforce that size, it is recommended that implementations make requests of that size. (TheoryOrg spec)
	private static BitSet completedPieces; // Whether the Pieces/blocks of the file are completed or not
	private static BitSet inProgress;
	private static long lastTrackerCommunication;
	
	public static int totalPieceCount;
	
	/**
	 * Usage: "java bittortoise <torrent_file> [<destination_file> [port]] [-v]" 
	 * 
	 * @param args
	 */
	public static void main(String args[])
	{
		// Torrent file, tracker, argument, and other parsed variables:
		int numConnections; // the number of TCP connections we currently have with other peers
		int port; // the port we are listening on
		List<Peer> peerList; // list of Peer objects that we got from the tracker
		int interval; // seconds the client should wait before sending a regular request to the tracker
		int min_interval; // seconds the client must wait before sending a regular request to the tracker
		String tracker_id; // a string to send back on next announcements
		int complete; // number of seeders/peers with the entire file
		int incomplete; // number of leechers/peers providing 0+ parts of the file (but are not seeders)
		byte[] my_peer_id = new byte[20]; // the peer id that this client is using
		Tracker tracker = null;
		Date start;
		Date finish;
		
		// State variables:
		totalPieceCount = 0;
		Map<SocketChannel, Peer> activePeerMap = new HashMap<SocketChannel, Peer>();
		Map<SocketChannel, Peer> pendingPeerMap = new HashMap<SocketChannel, Peer>();
		
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
		if(args.length < 1 || args.length > 4)
		{
			System.err.println("Usage: java bittortoise <torrent_file> [-d <destination_file>] [-p <port>] [-v]");
			System.exit(1);
		}
		port = 6881; // default port is 6881
		if(args.length == 3)
			port = Integer.parseInt(args[2]);
		
		String destinationFileName = null;
		
		boolean destinationFileIsNext = false;
		boolean portIsNext = false;
		for(String arg : args)
		{
			if(arg.startsWith("-"))
			{
				if(arg.indexOf('v') != -1)
					verbose = true;
				if(arg.indexOf('p') != -1)
					portIsNext = true;
				if(arg.indexOf('d') != -1)
					destinationFileIsNext = true;
			}
			else
			{
				if(portIsNext)
				{
					port = Integer.parseInt(arg);
					portIsNext = false;
				}
				else if(destinationFileIsNext)
				{
					destinationFileName = arg;
					destinationFileIsNext = false;
				}
			}
		}
		
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
		inProgress = new BitSet(totalPieceCount);
		
		// initialize all block requests for transfer:
		for(int i=0; i < BitTortoise.totalPieceCount - 1; i++)
		{
			BitTortoise.outstandingPieces.put(new Integer(i), new Piece(i));
			BitTortoise.outstandingPieces.get(i).addBlock(0 * BitTortoise.block_length, BitTortoise.block_length, null, null);
			for(int j=1; j < torrentFile.piece_length / BitTortoise.block_length; j++)
			{
				BlockRequest prev = BitTortoise.outstandingPieces.get(i).getBlock((j-1)* BitTortoise.block_length);
				BlockRequest justAdded = BitTortoise.outstandingPieces.get(i).addBlock(j * BitTortoise.block_length, BitTortoise.block_length, prev, null);
				prev.next = justAdded;
			}
			if(torrentFile.piece_length % BitTortoise.block_length != 0)
			{
				int j = torrentFile.piece_length / block_length;
				int k = torrentFile.piece_length % BitTortoise.block_length;
				BlockRequest prev = BitTortoise.outstandingPieces.get(i).getBlock((j-1)* BitTortoise.block_length);
				BlockRequest justAdded = BitTortoise.outstandingPieces.get(i).addBlock(j * BitTortoise.block_length, k, prev, null);
				prev.next = justAdded;
			}
		}
		// Fill the last piece with BlockRequest objects:
		BitTortoise.outstandingPieces.put(new Integer(BitTortoise.totalPieceCount - 1), new Piece(BitTortoise.totalPieceCount - 1));
		for(int j = 0; j < (torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * torrentFile.piece_length) / BitTortoise.block_length; j++)
		{
			BlockRequest prev = null;
			if(j != 0)
			{
				prev = BitTortoise.outstandingPieces.get(new Integer(BitTortoise.totalPieceCount - 1)).getBlock(j - 1);
			}
			
			BlockRequest br = BitTortoise.outstandingPieces.get(BitTortoise.totalPieceCount - 1).addBlock(j, BitTortoise.block_length);
			br.prev = prev;
			
			if(prev != null)
			{
				prev.next = br;
			}
		}
		if((torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * torrentFile.piece_length) % BitTortoise.block_length != 0)
		{
			int j = (torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * torrentFile.piece_length) / BitTortoise.block_length;
			int k = (torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * torrentFile.piece_length) % BitTortoise.block_length;
			BlockRequest prev = BitTortoise.outstandingPieces.get(BitTortoise.totalPieceCount - 1).getBlock((j-1)* BitTortoise.block_length);
			BlockRequest justAdded = BitTortoise.outstandingPieces.get(BitTortoise.totalPieceCount - 1).addBlock(j * BitTortoise.block_length, k, prev, null);
			
			if(prev != null)
			{
				prev.next = justAdded;
			}
		}
		
		if(BitTortoise.verbose)
			System.out.println("Finished parsing torrent file.");
		
		// Extract a list of peers, and other information from the tracker:
		peerList = new LinkedList<Peer>(); // List of peer objects (uses Generics)
		interval = 0; // seconds the client should wait before sending a regular request to the tracker
		min_interval = 0; // seconds the client must wait before sending a regular request to the tracker
		tracker_id = ""; // a string to send back on next announcements
		complete = 0; // number of seeders/peers with the entire file
		incomplete = 0; // number of leechers/peers providing 0+ parts of the file (but are not seeders)
		try
		{
			tracker = new Tracker(torrentFile);

			// Using the parsed torrent file, ping the tracker and get a list of peers to connect to:
			HttpURLConnection connection = (HttpURLConnection)(new URL(torrentFile.tracker_url + "?" +
					"info_hash=" + torrentFile.info_hash_as_url + "&" +
					"downloaded=0" + "&" +
					"uploaded=0" + "&" +
					"left=" + torrentFile.file_length + "&" +
					"peer_id=" + TorrentFileHandler.byteArrayToURLString(my_peer_id) + "&" +
					"port=" + port).openConnection());
			tracker.connect(connection, my_peer_id);
			peerList = tracker.peerList;
			BitTortoise.lastTrackerCommunication = (new Date()).getTime();
		}
		catch (UnknownHostException e)
		{
			System.err.println("Tracker is an unknown host: " + e.getMessage());
		}
		catch (IOException e) 
		{
			System.err.println("Error connecting to or reading from Tracker: " + e.getMessage());
		}

		if(BitTortoise.verbose)
			System.out.println("Finished parsing tracker results.");
		
		// Create the destination file:
		try
		{
			if(destinationFileName != null)
			{
				// If we were given a file name, use it:
				destinationFile = new RandomAccessFile(destinationFileName, "rw");
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
		
		if(BitTortoise.verbose)
			System.out.println("Finished creating destination file.");
		
		// Start the main loop of the client - choose and connect to peers, accept connections from peers, attempt to get all of the file
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
			start = new Date();
			while(true)
			{
				finish = new Date();
				long elapsedTimeMS = finish.getTime() - start.getTime();
				// if ten seconds has past, reorder the top peers get a new one
				// to opt unchoke.
				if ((elapsedTimeMS % (10*1000)) == 0 && elapsedTimeMS != 0)
				{
					ArrayList<Peer> possiblePeers = new ArrayList<Peer>();
					for(Map.Entry<SocketChannel, Peer> e : activePeerMap.entrySet())
					{
						if(e.getValue() != null)
						{
							possiblePeers.add(e.getValue());
						}
					}
					
					// sorts it based on bytesReadThisRound 
					// To avoid Array OOB errors, make sure there are at least 3, otherwise unchoke all
					Collections.sort(possiblePeers, new topThreeComparator());
					if(possiblePeers.size() > 3)
					{
						// Unchoke the top 3 peers that are sending us stuff, regardless... also make sure that there are (top) 3 interested peers unchoked
						int interested = 0;
						if(possiblePeers.get(0).peer_interested)
							interested++;
						if(possiblePeers.get(1).peer_interested)
							interested++;
						if(possiblePeers.get(2).peer_interested)
							interested++;
						
						possiblePeers.get(0).shouldUnchoke = true;
						possiblePeers.get(1).shouldUnchoke = true;
						possiblePeers.get(2).shouldUnchoke = true;
						
						int index = 3;
						while(interested < 3 && index < possiblePeers.size())
						{
							if(possiblePeers.get(index).peer_interested)
							{
								if(possiblePeers.get(index).am_choking)
									possiblePeers.get(index).shouldUnchoke = true;
								interested++;
							}
							index ++;
						}
					}
					else
					{
						for(Peer p : possiblePeers)
						{
							if(p.am_choking)
								p.shouldUnchoke = true;
						}
					}
					
					// get one to randomly unchoke
					int optimisticUnchokeIndex = (int)(Math.random() * (possiblePeers.size() - 3));
					optimisticUnchokeIndex = optimisticUnchokeIndex + 3;
					if(optimisticUnchokeIndex > 0 && optimisticUnchokeIndex < possiblePeers.size() && !possiblePeers.get(optimisticUnchokeIndex).shouldUnchoke && possiblePeers.get(optimisticUnchokeIndex).am_choking)
						possiblePeers.get(optimisticUnchokeIndex).shouldUnchoke = true;
					
					// go through and set the peers as choked if they aren't already
					for (int j = 0; j < possiblePeers.size(); j++)
					{
						Peer p = possiblePeers.get(j);
						if (p.am_choking == false && p.shouldUnchoke == false)
						{
							p.shouldChoke = true;
						}
						else
						{
							p.shouldChoke = false;
						}
						
						if(p.am_choking == true && p.shouldUnchoke == true)
						{
							p.shouldUnchoke = false;
						}
						
						if(p.am_choking == false  && p.shouldChoke == true)
						{
							p.shouldChoke = false;
						}
						p.bytesReadThisRound = 0;
					}
					start = new Date();
				}
				
				int num = select.selectNow();
				
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

									if(BitTortoise.verbose)
										System.out.println("New outgoing connection finished. (Peer " + newConnection.socket().getInetAddress().getHostAddress() + ":" + newConnection.socket().getPort() + ")");
								}
							}
							else if(key.isConnectable())
							{
								SocketChannel sc = (SocketChannel)key.channel();
								Peer p = pendingPeerMap.get(sc);
								
								if(sc.finishConnect())
								{
									try
									{
										sc.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
										
										pendingPeerMap.remove(sc);
										activePeerMap.put(sc, p);
										
										// Send handshake message to the peer:
										sc.write(ByteBuffer.wrap(p.handshake));
										
										// Update situation:
										p.handshake_sent = true;
										
										if(BitTortoise.verbose)
											System.out.println("New outgoing connection finished. (Peer " + p.ip + ":" + p.port + ")");
									}
									catch(IOException e)
									{
										System.err.println("Could not open new connection to peer - " + e.getMessage());
										
										if(activePeerMap.containsValue(p))
										{
											removePeer(p, activePeerMap);
										}
										key.cancel();
										numConnections--;
									}
								}
								else
								{
									System.err.println("Could not open new connection to peer.");
									
									if(pendingPeerMap.containsValue(p))
									{
										removePeer(p, pendingPeerMap);
									}
									key.cancel();
									numConnections--;
								}
							}
							else if(key.isReadable())
							{
								SocketChannel sc = (SocketChannel)key.channel();
								// read, process inputs
								// Check if this SocketChannel is already mapping to a peer - if not, we can only accept a handshake from it - if so, we are cool
								int size = -1;
								if(activePeerMap.containsKey(sc))
								{
									if(!readAndProcess(activePeerMap.get(sc), sc, true))
									{
										key.cancel();
										if(activePeerMap.containsKey(sc) && activePeerMap.get(sc) != null)
											activePeerMap.remove(sc).cleanup();
										else
											activePeerMap.remove(sc);
										try
										{
											if(!sc.socket().isClosed())
												sc.socket().close();
										}
										catch(IOException e)
										{
											System.err.println("Error closing socket!");
										}
										
										numConnections--;
									}
								}
								else
								{
									ByteBuffer buf = ByteBuffer.allocate(1024);
									size = sc.read(buf);
									
									// The other host is trying to disconnect (gracefully):
									if(size < 0)
									{
										key.cancel();
										if(activePeerMap.containsKey(sc) && activePeerMap.get(sc) != null)
											activePeerMap.remove(sc).cleanup();
										else
											activePeerMap.remove(sc);
										try
										{
											if(sc.isOpen())
												sc.close();
										}
										catch(IOException e)
										{
											System.err.println("Error closing socket!");
										}
										
										numConnections--;
									}
									
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
											
											if(!activePeerMap.containsValue(connectedTo))
											{
												activePeerMap.put(sc, connectedTo);
											}
											else
											{
												// We have already added this connection to the map - ignore ?
											}
											connectedTo.handshake_received = true;
											
											connectedTo.bytesLeft = size;
											connectedTo.readBuffer = buf;

											if(BitTortoise.verbose)
												System.out.println("New incoming connection finished (received handshake). (Peer " + connectedTo.ip + ":" + connectedTo.port + ")");
										}
										
										if(size != 0)
										{
											if(!readAndProcess(activePeerMap.get(sc), sc, false))
											{
												key.cancel();
												if(activePeerMap.containsKey(sc) && activePeerMap.get(sc) != null)
													activePeerMap.remove(sc).cleanup();
												else
													activePeerMap.remove(sc);
												try
												{
													if(sc.isOpen())
														sc.close();
												}
												catch(IOException e)
												{
													System.err.println("Error closing socket!");
												}

												numConnections--;
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
								
								// Attempt to write to this peer iff it is a peer that we have an active connection with
								// Note: incoming connections from which we have not yet received the handshake will not be in the activePeerMap
								if(activePeerMap.containsKey(sc))
								{
									Peer writablePeer = activePeerMap.get(sc);
									writablePeer.sendMessage(sc, BitTortoise.completedPieces, BitTortoise.inProgress, BitTortoise.outstandingPieces);
								}
							}
							else
								System.out.println("other");
						}
						catch(IOException e)
						{
							System.err.println("IO error - " + e.getMessage());
							if(activePeerMap.containsKey((SocketChannel)key.channel()))
								activePeerMap.get((SocketChannel)key.channel()).cleanup();
							if(pendingPeerMap.containsKey((SocketChannel)key.channel()))
								pendingPeerMap.get((SocketChannel)key.channel()).cleanup();
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
							
							if(!activePeerMap.containsValue(toConnect) && !pendingPeerMap.containsValue(toConnect))
							{
								// Send handshake to peer:
								SelectionKey temp = null;
								try
								{
									// Open a new connection to the peer, set to not block:
									SocketChannel sc = SocketChannel.open();
									sc.configureBlocking(false);
									sc.connect(new InetSocketAddress(toConnect.ip, toConnect.port));
									
									// Register the new connection with the selector:
									temp = sc.register(select, SelectionKey.OP_CONNECT);
									
									// Add the new peer to the Map:
									pendingPeerMap.put(sc, toConnect);
									
									succeeded = true;
									
									numConnections++;
									
									if(BitTortoise.verbose)
										System.out.println("New outgoing connection started. (Peer " + toConnect.ip + ":" + toConnect.port + ")");
								}
								catch(IOException e)
								{
									System.err.println("Could not open new connection to peer - " + e.getMessage());
									
									if(pendingPeerMap.containsValue(toConnect))
									{
										removePeer(toConnect, pendingPeerMap);
									}
									
									peerList.remove(last);
									
									if(temp != null)
										temp.cancel();
									
									numConnections--;
								}
								peerList.remove(last);
							}
							else
							{
								// Remove from the list.
								peerList.remove(last);
							}
						}
						else
						{
							succeeded = true;
						}
					}
				}
				else if(numConnections < 30 && (new Date()).getTime() - tracker.min_interval * 1000 > BitTortoise.lastTrackerCommunication)
				{
					HttpURLConnection tempConnection = (HttpURLConnection)(new URL(torrentFile.tracker_url + "?" +
							"info_hash=" + torrentFile.info_hash_as_url + "&" +
							"downloaded=0" + "&" +
							"uploaded=0" + "&" +
							"left=" + torrentFile.file_length + "&" +
							"peer_id=" + TorrentFileHandler.byteArrayToURLString(my_peer_id) + "&" +
							"port=" + port).openConnection());
					
					tracker.connect(tempConnection,my_peer_id);
					
					/*only add new peers to the list*/
					for(int i=0;i<tracker.peerList.size();i++){
						if(!peerList.contains(tracker.peerList.get(i))){
							peerList.add(tracker.peerList.get(i));
						}
					}
					BitTortoise.lastTrackerCommunication = (new Date()).getTime();
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
	

	public static boolean readAndProcess(Peer p, SocketChannel socketChannel, boolean readFirst)
	{
		boolean cont = true;
		if(readFirst)
		{
			// Information has not been read from the SocketChannel yet.. Do so
			try
			{
				int l = socketChannel.read(p.readBuffer);
				
				// If the other side is (orderly) trying to shut down the connection: 
				if(l == -1)
					return false;
				
				p.bytesLeft += l;
				p.readBuffer.position(0);
			}
			catch(IOException e)
			{
				return false;
			}
		}
		
		if(BitTortoise.verbose)
			System.out.println("New " + p.bytesLeft + " byte message (" + Peer.getBytesAsHex(p.readBuffer.array()) + ") received. (Peer " + p.ip + ":" + p.port + ")");
		
		if (p.blockRequest != null && p.blockRequest.status == BlockRequest.STARTED)
		{
			byte[] block;
			int bytesLeftInBlock = p.blockRequest.length - p.blockRequest.bytesRead;
			if (p.bytesLeft <= bytesLeftInBlock)
			{
				block = new byte[p.bytesLeft];
				p.readBuffer.get(block, 0, p.bytesLeft);
				p.readBuffer.position(p.bytesLeft);
				p.readBuffer.compact();
				p.readBuffer.position(0);
				p.bytesLeft = 0;
			}
			else
			{
				block = new byte[bytesLeftInBlock];
				p.readBuffer.get(block, 0, bytesLeftInBlock);
				p.readBuffer.position(bytesLeftInBlock);
				p.readBuffer.compact();
				p.readBuffer.position(0);
				p.bytesLeft -= bytesLeftInBlock;
			}
			processPieceMessage(p, p.blockRequest.piece, p.blockRequest.offset, block);
			
			// If we have finished receiving this Piece message:
			if(p.blockRequest == null)
			{
				p.emptyFinishedRequests();
				p.fill(BitTortoise.completedPieces,BitTortoise.inProgress, BitTortoise.outstandingPieces);
			}
			
			if(BitTortoise.verbose)
				System.out.println("Continuation of Piece message received. (Peer " + p.ip + ":" + p.port + ")\n");
		}
		
		if(!p.handshake_received )
		{
			if(p.bytesLeft >= 68 && BitTortoise.isHandshakeMessage(p.readBuffer))
			{
				// Handshake Message Received:
				byte[] external_info_hash = new byte[20];
				byte[] external_peer_id = new byte[20];
				
				p.readBuffer.position(28);
				p.readBuffer.get(external_info_hash, 0, 20);
				
				p.readBuffer.position(48);
				p.readBuffer.get(external_peer_id, 0, 20);
				
				p.readBuffer.position(68);
				p.readBuffer.compact();
				p.readBuffer.position(0);
				p.bytesLeft -= 68;
				
				// Check if the info hash that was given matches the one we are providing (by wrapping in a ByteBuffer, using .equals)
				if(!ByteBuffer.wrap(external_info_hash).equals(ByteBuffer.wrap(p.info_hash)))
				{
					// Peer requested connection for a bad info hash - Throw out connection ?
					return false;
				}
				
				p.handshake_received = true;
				
				if(BitTortoise.verbose)
					System.out.println("Handshake message received. (Peer " + p.ip + ":" + p.port + ")\n");
			}
			else
			{
				return false;
			}
		}
		while(p.bytesLeft >= 4 && cont)
		{
			// Attempt to read (may be a partial message)
			
			// While there are still messages in the queue:
			int length = p.readBuffer.getInt(0);
			
			if(length == 0)
			{
				// Keep-Alive message
				p.readBuffer.position(4);
				p.readBuffer.compact();
				p.readBuffer.position(0);
				
				p.bytesLeft -= 4;
				
				if(BitTortoise.verbose)
					System.out.println("Keep-alive message received. (Peer " + p.ip + ":" + p.port + ")\n");
			}
			else if(length >= 1 && p.bytesLeft >= 5)
			{
				byte id = p.readBuffer.get(4);
				if(id == 0)
				{
					// Choke Message Received:
					
					// Handle choke message:
					p.peer_choking = true;
					
					// Release all former requests:
					Iterator<BlockRequest> it = p.sendRequests.iterator();
					while(it.hasNext())
					{
						BlockRequest br = it.next();
						if(br.status != BlockRequest.FINISHED)
							br.status = BlockRequest.UNASSIGNED;
						it.remove();
					}
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println("Choke message received. (Peer " + p.ip + ":" + p.port + ")\n");
				}
				else if(id == 1)
				{
					// Un-choke Message Received:
					
					// Handle un-choke message:
					p.peer_choking = false;
					
					p.emptyFinishedRequests();
					p.fill(BitTortoise.completedPieces,BitTortoise.inProgress, BitTortoise.outstandingPieces);
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println("Unchoke message received. (Peer " + p.ip + ":" + p.port + ")\n");
				}
				else if(id == 2)
				{
					// Interested Message Received:
					
					// Handle Interested message:
					p.peer_interested = true;
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println("Interested message received. (Peer " + p.ip + ":" + p.port + ")\n");
				}
				else if(id == 3)
				{
					// Not Interested Message Received:
					
					// Handle Not Interested  message:
					p.peer_interested = false;
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println("Not Interested message received. (Peer " + p.ip + ":" + p.port + ")\n");
				}
				else if(id == 4)
				{
					if(p.bytesLeft < 9)
					{
						cont = false;
					}
					else
					{
						// Have Message Received:
						
						// Handle Have message:
						int piece_index = p.readBuffer.getInt(5);
						p.completedPieces.set(piece_index, true);
						
						// Set us to interested if they have something we want (and we are not already interested):
						BitSet need = (BitSet)p.completedPieces.clone();
						need.andNot(BitTortoise.completedPieces);
						
						if(!p.am_interested && !need.isEmpty())
							p.shouldInterest = true;
						
						// Perform state cleanup:
						p.readBuffer.position(9);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= 9;
						
						if(BitTortoise.verbose)
							System.out.println("Have (" + Integer.toHexString(piece_index) + ") message received. (Peer " + p.ip + ":" + p.port + ")\n");
					}
				}
				else if(id == 5)
				{
					if(p.bytesLeft < length + 4) // There might be a better way... (check the actual length of the file?)
					{
						cont = false;
					}
					else
					{
						// Bitfield Message Received:
						
						// Handle Bitfield message:
						byte[] ba = new byte[length - 1];
						p.readBuffer.position(5);
						p.readBuffer.get(ba);
						p.completedPieces = BitTortoise.bitSetFromByteArray(ba);
						
						// Set us to interested if they have something we want (and we are not already interested):
						BitSet need = (BitSet)p.completedPieces.clone();
						need.andNot(BitTortoise.completedPieces);
						
						if(!p.am_interested && !need.isEmpty())
							p.shouldInterest = true;
						
						// Perform state cleanup:
						p.readBuffer.position(length + 4);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= (length + 4);
						
						if(BitTortoise.verbose)
							System.out.println("Bitfield message received. (Peer " + p.ip + ":" + p.port + ")\n");
					}
				}
				else if(id == 6)
				{
					if(p.bytesLeft < 17)
					{
						cont = false;
					}
					else
					{
						// Request Message Received:
						
						// Handle Request message:
						int request_index = p.readBuffer.getInt(5);
						int request_begin = p.readBuffer.getInt(9);
						int request_length = p.readBuffer.getInt(13);
						
						if(!BitTortoise.completedPieces.get(request_index) && request_begin > 0 && request_begin < BitTortoise.torrentFile.piece_length && request_length > 0 && request_begin + request_length <= BitTortoise.torrentFile.piece_length)
							return false;
						
						// Queue this for sending at some point in the near future:
						if(!p.am_choking)
						{
							p.receiveRequests.add(new BlockRequest(request_index,request_begin,request_length));
						}
						
						// Perform state cleanup:
						p.readBuffer.position(17);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= 17;
						
						if(BitTortoise.verbose)
							System.out.println("Request (" + request_index + "," + request_begin + "," + request_length + ") message received. (Peer " + p.ip + ":" + p.port + ")\n");
					}
				}
				else if(id == 7)
				{
					// Note: handle size somehow...
					
					// Piece Message Received:
					
					// Handle Piece message:
					int piece_index = p.readBuffer.getInt(5);
					int block_begin = p.readBuffer.getInt(9);
					int block_length = length - 9;
					
					boolean requestExists = false;
					for(BlockRequest br : p.sendRequests)
					{
						if(br.piece == piece_index && br.offset == block_begin && br.length == block_length)
						{
							requestExists = true;
							break;
						}
					}
					if(!requestExists)
						return false;
					
					p.blockRequest = BitTortoise.outstandingPieces.get(piece_index).getBlock(block_begin);
					p.blockRequest.status = BlockRequest.STARTED;
					
					//convert bytebuffer into byte array and store it in 
					byte [] block = new byte[p.bytesLeft - 13];
					p.readBuffer.position(13);
					p.readBuffer.get(block, 0, p.bytesLeft - 13);
					processPieceMessage(p, piece_index, block_begin, block);
					
					// Note: the following should really be done within "processPieceMessage" instead of here, but whatever:
					// Perform state cleanup:
					int amountToTrim = Math.min(length + 4, p.bytesLeft);
					p.readBuffer.position(amountToTrim);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= (amountToTrim);
					
					if(BitTortoise.verbose)
						System.out.println("Piece (" + piece_index + "," + block_begin + "," + block_length + ") message received. (Peer " + p.ip + ":" + p.port + ")\n");
				}
				else if(id == 8)
				{
					if(p.bytesLeft < 17)
					{
						cont = false;
					}
					else
					{
						// Cancel Message Received:
						
						// Handle Cancel message:
						int cancel_index = p.readBuffer.getInt(5);
						int cancel_begin = p.readBuffer.getInt(9);
						int cancel_length = p.readBuffer.getInt(13);
						
						// Remove the piece request with those properties
						Iterator<BlockRequest> it = p.receiveRequests.iterator();
						while(it.hasNext())
						{
							BlockRequest br = it.next();
							if(br.piece == cancel_index && br.offset == cancel_begin && br.length == cancel_length)
							{
								it.remove();
							}
						}
						
						// Perform state cleanup:
						p.readBuffer.position(17);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= 17;
						
						if(BitTortoise.verbose)
							System.out.println("Cancel (" + cancel_index + "," + cancel_begin + "," + cancel_length + ") message received. (Peer " + p.ip + ":" + p.port + ")\n");
					}
				}
				else
				{
					// Unrecognized id, ignore the rest of length bytes
					if(p.bytesLeft < length + 4)
					{
						cont = false;
						if(length + 4 >= Peer.BYTES_TO_ALLOCATE)
						{
							p.bytesLeft = 0;
							p.readBuffer.position(p.readBuffer.capacity());
							p.readBuffer.compact();
							p.readBuffer.position(0);
						}
					}
					else
					{
						p.readBuffer.position(length + 4);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= (length + 4);
						
						if(BitTortoise.verbose)
							System.out.println("Unknown message received. (Peer " + p.ip + ":" + p.port + ")\n");
					}
				}
			}
			else
			{
				System.err.println("Disconnecting from peer - Received bad data");
				return false;
			}
		}
		
		if(p.bytesLeft >= 0)
		{
			p.readBuffer.position(p.bytesLeft);
		}
		
		return true;
	}
	
	public static byte[] getPiece(int index, int begin, int length) {
		int fileOffset = (index * torrentFile.piece_length) + begin; 
		byte [] byteArray = new byte[length];
		try
		{
			destinationFile.seek(fileOffset);
			destinationFile.read(byteArray, 0, length);
		}
		catch(IOException e)
		{
			System.err.println("Error occurred while getting Piece " + index + ".");
		}
		return MessageLibrary.getPieceMessage(index, begin, length, byteArray);
	}
	
	public static boolean storePiece(Peer p, int piece_index, int piece_begin, byte [] block)
	{
		int fileOffset = (piece_index * torrentFile.piece_length) + piece_begin + p.blockRequest.bytesRead; 
		try
		{
			destinationFile.seek(fileOffset);
			destinationFile.write(block, 0, block.length);
		}
		catch(IOException e)
		{
			System.err.println("Error occurred while storing Piece " + piece_index + ".");
			return false;
		}
		
		return true;
	}
	
	public static boolean processPieceMessage(Peer p, int piece_index, int block_begin, byte[] block) {
		// Update the number of bytes read this round for this peer:
		p.bytesReadThisRound += block.length;
		
		// Update the last time modified:
		p.blockRequest.timeModified = (new Date()).getTime();
		
		// Do other stuff (by KENNY!):
		if (!storePiece(p, piece_index, block_begin, block)) {
			return false;
		}
		p.blockRequest.bytesRead += block.length;
		if (p.blockRequest.bytesRead >= p.blockRequest.length) { //if done reading block
			p.blockRequest.status = BlockRequest.FINISHED;
			p.blockRequest = null; //this peer is open to receive a new block
			if (outstandingPieces.get(piece_index).allFinished())
			{
				byte [] entirePiece = new byte [torrentFile.piece_length]; //this is a HUGE array, is there a better way to do this?
				byte [] mySHA1;
				try
				{
					destinationFile.seek(piece_index * torrentFile.piece_length);
					destinationFile.read(entirePiece, 0, torrentFile.piece_length);
				}
				catch(Exception e)
				{
					System.out.println("error reading in entire piece");
					System.exit(1);
				}
				mySHA1 = SHA1Functions.getSha1Hash(entirePiece);
				if (ByteBuffer.wrap(mySHA1).equals(ByteBuffer.wrap((byte[])torrentFile.piece_hash_values_as_binary.get(piece_index))))
				{
					outstandingPieces.remove(piece_index);
					completedPieces.set(piece_index);
					if (outstandingPieces.isEmpty())
					{
						System.out.println("received entire file... do something");
					}
					// The piece has been finished:
					BitTortoise.inProgress.set(piece_index, false);
					BitTortoise.completedPieces.set(piece_index, true);
					System.out.println("Completed piece " + piece_index);
				}
				else
				{
					System.err.println("Error!");
					outstandingPieces.get(piece_index).resetAll();
				}
			}
		}
		return true;
	}
}
