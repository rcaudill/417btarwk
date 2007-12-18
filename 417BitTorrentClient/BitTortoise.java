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
import java.text.*;

public class BitTortoise
{
	public static boolean verbose;
	public static boolean useExtenstions;
	
	public static final int MAX_OUTSTANDING_REQUESTS = 200;
	public static final int MIN_OUTSTANDING_REQUESTS = 5;
	public static final int OUTSTANDING_REQUEST_RATE = 2;
	public static final int NUM_TO_UNCHOKE = 3;
	public static final int numToGet = 100; // try to get 100 total peers from the tracker for the list
	
	private static TorrentFile torrentFile; // the object into which the .torrent file is b-decoded
	private static RandomAccessFile destinationFile; // The file into which we are writing
	private static Map<Integer, Piece> outstandingPieces = new HashMap<Integer, Piece>();
	private static ArrayList<Piece> rarity = new ArrayList<Piece>(); //orders pieces by rarity
	private static int block_length = 16384; //The reality is near all clients will now use 2^14 (16KB) requests. Due to clients that enforce that size, it is recommended that implementations make requests of that size. (TheoryOrg spec)
	private static BitSet completedPieces; // Whether the Pieces/blocks of the file are completed or not
	private static BitSet inProgress;
	private static long lastTrackerCommunication;
	private static Map<SocketChannel, Peer> activePeerMap;
	private static Map<SocketChannel, Peer> pendingPeerMap;
	public static long totalUploaded;
	public static long totalDownloaded;
	public static Set<String> connectedIDs;
	
	public static int numUnchoked;
	
	private static boolean isIncomplete;
	private static boolean continueSeeding;
	private static boolean quitNotReceived;
	private static boolean initialSeeding;
	public static int totalPieceCount;
	private static long startT;
	private static long finishT;
	
	/**
	 * Usage: "java BitTortoise <torrent_file> [-d <destination_file>] [-p <port>] [-v] [-s] [-c] [-n] [-r <bit tortoise resume info file>]" 
	 * -d means that you want the file to use the given filename
	 * -p means that you want to use the given port
	 * -v means that you want to run in verbose mode
	 * -s means that you want to start out seeding
	 * -c means that you want to continue seeding when done with the transfer
	 * -r means that you want to use the given resume info file (and are resuming an incomplete download)
	 * -n means that you DO NOT want to use the extensions that we have added to the program
	 * 
	 * @param args
	 */
	public static void main(String args[])
	{
		// SECTION: Initialize variables:
		
		
		
		// Torrent file, tracker, argument, and other parsed variables:
		int numConnections; // the number of TCP connections we currently have with other peers
		int port; // the port we are listening on
		List<Peer> peerList; // list of Peer objects that we got from the tracker
		byte[] my_peer_id = new byte[20]; // the peer id that this client is using
		String my_key = new String();
		for(int i = 0; i < 8; i++)
			my_key += Integer.toHexString((int)(Math.random() * 16.0));
		
		Tracker tracker = null;
		BitTortoise.initialSeeding = false;
		BitTortoise.continueSeeding = false;
		BitTortoise.isIncomplete = true;
		BitTortoise.quitNotReceived = true;
		BitTortoise.totalUploaded = 0;
		BitTortoise.totalDownloaded = 0;
		String resumeInfoFilename = null;
		connectedIDs = new TreeSet<String>();
		
		long startTime = (new Date()).getTime();
		
		// State variables:
		BitTortoise.totalPieceCount = 0;
		activePeerMap = new HashMap<SocketChannel, Peer>();
		pendingPeerMap = new HashMap<SocketChannel, Peer>();
		
		
		
		// END of SECTION: Initialize variables
		
		
		
		// SECTION: Generate Peer ID:
		
		
		
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
		
		
		
		// END of SECTION: Generate Peer ID
		
		
		
		// SECTION: Parse command-line arguments:
		
		
		
		// Verify that the correct argument(s) were used:
		if(args.length < 1 || args.length > 9)
		{
			System.out.println("Usage: java BitTortoise <torrent_file> [-d <destination_file>] [-p <port>] [-v] [-s] [-c] [-n] [-r <bit tortoise resume info file>]");
			System.exit(1);
		}
		port = 6881; // default port is 6881
		
		String destinationFileName = null;
		
		
		BitTortoise.verbose = false;
		BitTortoise.useExtenstions = true;
		boolean destinationFileIsNext = false;
		boolean portIsNext = false;
		boolean resumeFileIsNext = false;
		for(String arg : args)
		{
			if(arg.startsWith("-"))
			{
				if(arg.indexOf('v') != -1)
					BitTortoise.verbose = true;
				if(arg.indexOf('c') != -1)
					BitTortoise.continueSeeding = true;
				if(arg.indexOf('s') != -1)
					BitTortoise.initialSeeding = true;
				if(arg.indexOf('p') != -1)
					portIsNext = true;
				if(arg.indexOf('d') != -1)
					destinationFileIsNext = true;
				if(arg.indexOf('r') != -1)
					resumeFileIsNext = true;
				if(arg.indexOf('n') != -1)
					BitTortoise.useExtenstions = false;
				
				if((portIsNext && destinationFileIsNext) || (portIsNext && resumeFileIsNext) || (destinationFileIsNext && resumeFileIsNext))
				{
					System.out.println("java BitTortoise <torrent_file> [-d <destination_file>] [-p <port>] [-v] [-s] [-c] [-n] [-r <bit tortoise resume info file>]");
					System.exit(1);
				}
			}
			else
			{
				if(portIsNext)
				{
					port = Integer.parseInt(arg);
					portIsNext = false;
				}
				if(destinationFileIsNext)
				{
					destinationFileName = arg;
					destinationFileIsNext = false;
				}
				if(resumeFileIsNext)
				{
					resumeInfoFilename = arg;
					resumeFileIsNext = false;
				}
			}
		}
		if(BitTortoise.initialSeeding && resumeInfoFilename != null)
		{
			System.out.println("java BitTortoise <torrent_file> [-d <destination_file>] [-p <port>] [-v] [-s] [-c] [-r <bit tortoise resume info file>]");
			System.exit(1);
		}
		
		
		// END of SECTION: Parse command-line arguments
		
		
		
		// SECTION: Parse torrent file:
		
		
		
		// Parse the torrent file.
		BitTortoise.torrentFile = new TorrentFile();
		// Note: this was put in a try block because this sometimes breaks when reading a bad torrent file
		try
		{
			TorrentFileHandler torrentFileHandler = new TorrentFileHandler();
			torrentFile = torrentFileHandler.openTorrentFile(args[0]);
		}
		catch(Exception e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": The provided file was not of the appropriate format, or could not be read.");
			System.exit(1);
		}
		
		BitTortoise.totalPieceCount = ((int)BitTortoise.torrentFile.file_length/BitTortoise.torrentFile.piece_length) + (((BitTortoise.torrentFile.file_length % BitTortoise.torrentFile.piece_length) == 0)? (0) : (1));
		BitTortoise.completedPieces = new BitSet(BitTortoise.totalPieceCount);
		BitTortoise.inProgress = new BitSet(BitTortoise.totalPieceCount);
		
		if(BitTortoise.verbose)
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Finished parsing torrent file.  Info Hash = " + BitTortoise.torrentFile.info_hash_as_url);
		
		
		
		// END of SECTION: Parse torrent file
		
		
		
		// SECTION: Create destination file:
		
		
		
		// Create the destination file:
		try
		{
			// If we were not given a file name, use the string preceding ".torrent" in the torrent file:
			// Ex. "testTorrentFile.txt.torrent" -> "testTorrentFile.txt"
			if(destinationFileName == null)
			{
				destinationFileName = args[0].substring(0,args[0].lastIndexOf(".torrent"));
			}
			BitTortoise.destinationFile = new RandomAccessFile(destinationFileName, "rw");
			
			// Set the file to the total length of the file:
			if(!BitTortoise.initialSeeding && resumeInfoFilename == null)
			{
				BitTortoise.destinationFile.setLength(torrentFile.file_length);
			}
		}
		catch(IOException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Error creating file: " + e.getMessage());
			System.exit(1);
		}
		
		if(BitTortoise.verbose)
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Finished creating destination file.");
		
		
		
		// END of SECTION: Create destination file
		
		
		
		// SECTION: fill outstandingPieces map with pieces that need to be finished:
		
		
		
		// If this is not a resume or a seeding attempt, fill the blocks in:
		if(!BitTortoise.initialSeeding && resumeInfoFilename == null)
		{
			// initialize all block requests for transfer:
			for(int i=0; i < BitTortoise.totalPieceCount - 1; i++)
			{
				BitTortoise.outstandingPieces.put(new Integer(i), new Piece(i));
				BitTortoise.outstandingPieces.get(i).addBlock(0 * BitTortoise.block_length, BitTortoise.block_length, null, null);
				for(int j=1; j < BitTortoise.torrentFile.piece_length / BitTortoise.block_length; j++)
				{
					BlockRequest prev = BitTortoise.outstandingPieces.get(i).getBlock((j-1)* BitTortoise.block_length);
					BlockRequest justAdded = BitTortoise.outstandingPieces.get(i).addBlock(j * BitTortoise.block_length, BitTortoise.block_length, prev, null);
					prev.next = justAdded;
				}
				if(BitTortoise.torrentFile.piece_length % BitTortoise.block_length != 0)
				{
					int j = BitTortoise.torrentFile.piece_length / block_length;
					int k = BitTortoise.torrentFile.piece_length % BitTortoise.block_length;
					BlockRequest prev = BitTortoise.outstandingPieces.get(i).getBlock((j-1)* BitTortoise.block_length);
					BlockRequest justAdded = BitTortoise.outstandingPieces.get(i).addBlock(j * BitTortoise.block_length, k, prev, null);
					prev.next = justAdded;
				}
			}
			// Fill the last piece with BlockRequest objects:
			BitTortoise.outstandingPieces.put(new Integer(BitTortoise.totalPieceCount - 1), new Piece(BitTortoise.totalPieceCount - 1));
			for(int j = 0; j < (BitTortoise.torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * BitTortoise.torrentFile.piece_length) / BitTortoise.block_length; j++)
			{
				BlockRequest prev = null;
				if(j != 0)
				{
					prev = BitTortoise.outstandingPieces.get(new Integer(BitTortoise.totalPieceCount - 1)).getBlock((j - 1) * BitTortoise.block_length);
				}
				
				BlockRequest br = BitTortoise.outstandingPieces.get(BitTortoise.totalPieceCount - 1).addBlock(j * BitTortoise.block_length, BitTortoise.block_length);
				br.prev = prev;
				
				if(prev != null)
				{
					prev.next = br;
				}
			}
			if((BitTortoise.torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * BitTortoise.torrentFile.piece_length) % BitTortoise.block_length != 0)
			{
				int j = (BitTortoise.torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * BitTortoise.torrentFile.piece_length) / BitTortoise.block_length;
				int k = (BitTortoise.torrentFile.file_length - (BitTortoise.totalPieceCount - 1) * BitTortoise.torrentFile.piece_length) % BitTortoise.block_length;
				BlockRequest prev = BitTortoise.outstandingPieces.get(BitTortoise.totalPieceCount - 1).getBlock((j-1)* BitTortoise.block_length);
				BlockRequest justAdded = BitTortoise.outstandingPieces.get(BitTortoise.totalPieceCount - 1).addBlock(j * BitTortoise.block_length, k, prev, null);
				
				if(prev != null)
				{
					prev.next = justAdded;
				}
			}
			
			if(BitTortoise.verbose)
				System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Finished filling map of Block Requests.");
		}
		else
		{
			// Do resuming/seeding checks, and resume/seed if necessary
			if(resumeInfoFilename != null)
			{
				if(!Resumer.resumeFromStopped(resumeInfoFilename, destinationFile, BitTortoise.torrentFile, BitTortoise.outstandingPieces, BitTortoise.completedPieces, BitTortoise.inProgress, BitTortoise.totalPieceCount))
				{
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Could not resume from the given file.");
					System.exit(1);
				}
				else
				{
					if(BitTortoise.verbose)
						System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Resumed from the given file...");
				}
			}
			if(BitTortoise.initialSeeding)
			{
				if(!Resumer.checkSeed(destinationFile, BitTortoise.torrentFile))
				{
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Seed file failed SHA1 hash check.");
					System.exit(1);
				}
				else
				{
					BitTortoise.completedPieces.set(0, BitTortoise.totalPieceCount, true);
					BitTortoise.inProgress.set(0, BitTortoise.totalPieceCount, false);
					
					if(BitTortoise.verbose)
						System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Seed file parsed.");
				}
			}
		}
		
		//fills up rarity with all outstanding pieces
		for(Map.Entry<Integer, Piece> e : outstandingPieces.entrySet())
		{
			rarity.add(e.getValue());
		}
		
		
		// END of SECTION: fill outstandingPieces map with pieces that need to be finished
		
		
		
		// SECTION: connect to tracker:
		
		
		
		// Extract a list of peers, and other information from the tracker:
		peerList = new LinkedList<Peer>(); // List of peer objects
		try
		{
			tracker = new Tracker(BitTortoise.torrentFile);
			tracker.key = my_key;
			
			// Using the parsed torrent file, ping the tracker and get a list of peers to connect to:
			String connectionString = BitTortoise.torrentFile.tracker_url + "?" + 
						"info_hash=" + BitTortoise.torrentFile.info_hash_as_url + "&" + 
						"peer_id=" + TorrentFileHandler.byteArrayToURLString(my_peer_id) + "&" + 
						"port=" + port + "&";
			
			// Advertise to tracker differently if we are starting out seeding versus leeching 
			if(BitTortoise.initialSeeding)
			{
				connectionString += "uploaded=0" + "&" +
						"downloaded=0" + "&" + 
						"left=0" + "&";
			}
			else
			{
				connectionString += "uploaded=0" + "&" + 
						"downloaded=0" + "&" + 
						"left=" + BitTortoise.torrentFile.file_length + "&";
			}
			
			connectionString += "key=" + my_key + "&" + 
						"event=started" + "&" + 
						"numwant=" + numToGet + "&" + 
						"compact=1" + "&" + 
						"no_peer_id=1";
			
			HttpURLConnection connection = (HttpURLConnection)(new URL(connectionString).openConnection());
			tracker.connect(connection, my_peer_id);
			peerList = tracker.peerList;
			BitTortoise.lastTrackerCommunication = (new Date()).getTime();
		}
		catch (UnknownHostException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Tracker is an unknown host: " + e.getMessage());
		}
		catch (IOException e) 
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Error connecting to or reading from Tracker: " + e.getMessage());
		}

		if(BitTortoise.verbose)
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Finished parsing tracker results.");
		
		
		
		// END of SECTION: connect to tracker
		
		
		
		// SECTION: main loop:
		
		
		
		// Start the main loop of the client - choose and connect to peers, accept connections from peers, attempt to get all of the file
		numConnections = 0;
		numUnchoked = 0;
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
			startT = new Date().getTime();
			while((BitTortoise.isIncomplete || BitTortoise.continueSeeding) && quitNotReceived)
			{
				int commandLength = System.in.available();
				if(commandLength != 0)
				{
					byte[] read = new byte[commandLength];
					System.in.read(read, 0, commandLength);
					if(read[0] == 'q')
					{
						quitNotReceived = false;
						break;
					}
					else if (read[0] == 'a') {
						String newIP = new String(), newPort = new String();
						boolean finishedIP = false;
						for (int i=2;i<commandLength-2; i++) {
							if ((char)read[i] == ':')
							{
								finishedIP = true;
							} 
							else 
							{
								if (!finishedIP)
								{
									newIP += (char)read[i];
								} 
								else
								{
									newPort += (char)read[i];
								}
							}
						}
						peerList.add(new Peer(torrentFile.info_hash_as_binary, new byte[20], new byte[20], newIP, Integer.parseInt(newPort)));
						System.out.println("Add Peer: " + newIP + ":" + newPort);		
					}
				}
				
				finishT = new Date().getTime();
				long elapsedTimeMS = finishT - startT;
				// if ten seconds has past, reorder the top peers get a new one
				// to opt unchoke.
				if (((elapsedTimeMS % (10000)) == 0) && (elapsedTimeMS != 0))
				{
					unchokePeers();
					
					printStatus();
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
									try
									{
										// Incoming Connection to the server channel/socket:
										// Accept the connection, set it to not block:
										SocketChannel newConnection = serverChannel.accept();
										
										newConnection.configureBlocking(false);
										
										// Register the connection with the selector
										newConnection.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
										
										numConnections ++;
										
										if(BitTortoise.verbose)
											System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + newConnection.socket().getInetAddress().getHostAddress() + ":" + newConnection.socket().getPort() + "): Incoming connection finished.");
									}
									catch(IOException e)
									{
										System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Error receiving new connection from peer.");
									}
								}
							}
							else if(key.isConnectable())
							{
								SocketChannel sc = (SocketChannel)key.channel();
								Peer p = pendingPeerMap.get(sc);
								try
								{
									if(sc.finishConnect())
									{
										if(BitTortoise.verbose)
											System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Outgoing connection finished.");
										
										sc.register(select, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
										
										pendingPeerMap.remove(sc);
										activePeerMap.put(sc, p);
										/*
										// Send handshake message to the peer:
										sc.write(ByteBuffer.wrap(p.handshake));
										
										// Update situation:
										p.handshake_sent = true;
										*/
									}
									else
									{
										System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Could not open new connection to peer.");
										
										if(pendingPeerMap.containsValue(p))
										{
											removePeer(p, pendingPeerMap);
										}
										connectedIDs.remove(new String(p.peer_id));
										key.cancel();
										numConnections--;
									}
								}
								catch(IOException e)
								{
									System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Could not open new connection to peer.  " + e.getMessage());
									
									if(activePeerMap.containsValue(p))
									{
										removePeer(p, activePeerMap);
									}
									if(pendingPeerMap.containsValue(p))
									{
										removePeer(p, pendingPeerMap);
									}
									connectedIDs.remove(new String(p.peer_id));
									key.cancel();
									numConnections--;
								}
							}
							else if(key.isReadable())
							{
								SocketChannel sc = (SocketChannel)key.channel();
								// read, process inputs
								// Check if this SocketChannel is already mapping to a peer - if not, we can only accept a handshake from it - if so, we are cool
								String ipAndPort = sc.socket().getInetAddress().getHostAddress() + ":" + sc.socket().getPort();
								int size = -1;
								if(activePeerMap.containsKey(sc))
								{
									if(!readAndProcess(activePeerMap.get(sc), sc, true))
									{
										key.cancel();
										String peerID = new String(activePeerMap.get(sc).peer_id);
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
											System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + ipAndPort + "): Error closing socket!");
										}
										
										connectedIDs.remove(peerID);
										
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
										String peerID = "0";
										if(activePeerMap.containsKey(sc) && activePeerMap.get(sc) != null)
										{
											peerID = new String(activePeerMap.get(sc).peer_id);
											activePeerMap.remove(sc).cleanup();
										}
										else
											activePeerMap.remove(sc);
										try
										{
											if(sc.isOpen())
												sc.close();
										}
										catch(IOException e)
										{
											System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + ipAndPort + "): Error closing socket!");
										}
										
										connectedIDs.remove(peerID);
										
										numConnections--;
										
										System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + ipAndPort + "): Connection closed (gracefully).");
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
										if(!Arrays.equals(external_info_hash,torrentFile.info_hash_as_binary))
										{
											// Peer requested connection for a bad info hash - Throw out connection ?
										}
										else
										{
											Peer connectedTo = new Peer(torrentFile.info_hash_as_binary, external_peer_id, my_peer_id, sc.socket().getInetAddress().getHostAddress(), sc.socket().getPort());
											
											if(!activePeerMap.containsValue(connectedTo))
											{
												activePeerMap.put(sc, connectedTo);
												connectedIDs.add(new String(connectedTo.peer_id));
											}
											else
											{
												// We have already added this connection to the map - ignore ?
											}
											connectedTo.handshake_received = true;
											
											connectedTo.bytesLeft = size;
											connectedTo.readBuffer = buf;

											if(BitTortoise.verbose)
												System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + connectedTo.ip + ":" + connectedTo.port + "): Received Handshake message.");
										}
										
										if(size != 0)
										{
											if(!readAndProcess(activePeerMap.get(sc), sc, false))
											{
												key.cancel();
												String peerID = "0"; 
												if(activePeerMap.containsKey(sc) && activePeerMap.get(sc) != null)
												{
													peerID = new String(activePeerMap.get(sc).peer_id);
													activePeerMap.remove(sc).cleanup();
												}
												else
													activePeerMap.remove(sc);
												try
												{
													if(sc.isOpen())
														sc.close();
												}
												catch(IOException e)
												{
													System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + ipAndPort + "): Error closing socket!");
												}
												
												connectedIDs.remove(peerID);
												
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
									writablePeer.sendMessage(sc, BitTortoise.completedPieces, BitTortoise.inProgress, BitTortoise.rarity);
								}
							}
						}
						catch(IOException e)
						{
							System.out.println("IO error - " + e.getMessage());
							String peerID = "0";
							if(activePeerMap.containsKey((SocketChannel)key.channel()))
							{
								peerID = new String(activePeerMap.get((SocketChannel)key.channel()).peer_id);
								activePeerMap.get((SocketChannel)key.channel()).cleanup();
								activePeerMap.remove((SocketChannel)key.channel());
							}
							if(pendingPeerMap.containsKey((SocketChannel)key.channel()))
							{
								peerID = new String(activePeerMap.get((SocketChannel)key.channel()).peer_id);
								pendingPeerMap.get((SocketChannel)key.channel()).cleanup();
								pendingPeerMap.remove((SocketChannel)key.channel());
							}
							key.cancel();
							try
							{
								connectedIDs.remove(peerID);
							}
							catch (Exception e1) { }
						}
					}
				}
				
				// Clear the list:
				select.selectedKeys().clear();
				
				// Check the number of connections, add more if needed
				if(isIncomplete && numConnections < 30 && peerList.size() > 0)
				{
					boolean succeeded = false;
					while(!succeeded)
					{
						int last = peerList.size() - 1;
						if(last >= 0)
						{
							Peer toConnect = peerList.get(last);
							
							if(!connectedIDs.contains(new String(toConnect.peer_id)) && !activePeerMap.containsValue(toConnect) && !pendingPeerMap.containsValue(toConnect))
							{
								// Send handshake to peer:
								SelectionKey temp = null;
								try
								{
									// Open a new connection to the peer, set to not block:
									SocketChannel sc = SocketChannel.open();
									sc.configureBlocking(false);
									
									sc.connect(new InetSocketAddress(toConnect.ip, toConnect.port));
									
									connectedIDs.add(new String(toConnect.peer_id));
									
									// Register the new connection with the selector:
									temp = sc.register(select, SelectionKey.OP_CONNECT);
									
									// Add the new peer to the Map:
									pendingPeerMap.put(sc, toConnect);
									
									succeeded = true;
									
									numConnections++;
									
									if(BitTortoise.verbose)
										System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + toConnect.ip + ":" + toConnect.port + "): New outgoing connection started.");
								}
								catch(IOException e)
								{
									System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Could not open new connection to peer - " + e.getMessage());
									
									if(pendingPeerMap.containsValue(toConnect))
									{
										removePeer(toConnect, pendingPeerMap);
									}
									
									if(temp != null)
										temp.cancel();
									
									numConnections--;
									
									connectedIDs.remove(new String(toConnect.peer_id));
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
				else if(isIncomplete && numConnections < 30 && (((new Date()).getTime() - tracker.interval * 1000 > BitTortoise.lastTrackerCommunication) || (activePeerMap.size() == 0 && pendingPeerMap.size() == 0 && ((new Date()).getTime() - tracker.min_interval * 1000 > BitTortoise.lastTrackerCommunication))))
				{
					String connectionString = BitTortoise.torrentFile.tracker_url + "?" + 
							"info_hash=" + BitTortoise.torrentFile.info_hash_as_url + "&" + 
							"peer_id=" + TorrentFileHandler.byteArrayToURLString(my_peer_id) + "&" + 
							"port=" + port + "&" + 
							"uploaded=" + BitTortoise.totalUploaded + "&" + 
							"downloaded=" + BitTortoise.totalDownloaded + "&" + 
							"left=" + (BitTortoise.torrentFile.file_length - BitTortoise.totalDownloaded) + "&" + 
							"key=" + my_key + "&" + 
							"numwant=" + (numToGet - peerList.size()) + "&" + 
							"compact=1" + "&" + 
							((tracker.tracker_id == null)? ("") : ("trackerid=" + tracker.tracker_id + "&")) + 
							"no_peer_id=1";
					
					HttpURLConnection tempConnection = (HttpURLConnection)(new URL(connectionString).openConnection());
					
					tracker.connect(tempConnection,my_peer_id);
					
					// Only add new peers to the list
					for(int i=0;i<tracker.peerList.size();i++)
					{
						if(!peerList.contains(tracker.peerList.get(i)))
						{
							peerList.add(tracker.peerList.get(i));
						}
					}
					BitTortoise.lastTrackerCommunication = (new Date()).getTime();
				}
			}
		}
		catch(IOException e)
		{
			System.out.println("IOException Occurred! - " + e.getMessage());
			System.exit(1);
		}
		
		
		
		// END of SECTION: main loop
		
		
		
		// SECTION: Cleanup:
		
		
		
		if(!BitTortoise.isIncomplete)
		{
			for(Map.Entry<SocketChannel, Peer> ent : activePeerMap.entrySet())
			{
				try
				{
					ent.getKey().close();
				}
				catch(IOException e)
				{
					System.out.println("Error thrown while attempting to close SocketChannel - " + e.getMessage());
				}
			}
		}
		
		long timeTaken = (new Date()).getTime() - startTime;
		
		if(!BitTortoise.isIncomplete)
		{
			tracker.alertCompleted(BitTortoise.totalDownloaded, BitTortoise.totalUploaded, my_peer_id, port);
			tracker.alertStopped(BitTortoise.totalDownloaded, BitTortoise.totalUploaded, my_peer_id, port);
			
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Success!");
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": File received in " + timeTaken / 1000 + " seconds. Average download rate: " + ((((double)BitTortoise.torrentFile.file_length) / ((double)timeTaken)) * (((double)1000.0) / ((double)1024.0))) + " kB/s.");
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Average upload rate: " + ((((double)BitTortoise.totalUploaded) / ((double)timeTaken)) * (((double)1000.0) / ((double)1024.0))) + " kB/s.");
		}
		else if(!BitTortoise.quitNotReceived)
		{
			tracker.alertStopped(BitTortoise.totalDownloaded, BitTortoise.totalUploaded, my_peer_id, port);
			
			// Attempt to save the current status to resume from:
			if(Resumer.saveStatus(destinationFileName + ".btri", BitTortoise.outstandingPieces))
			{
				System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Resume file saved as " + destinationFileName + ".btri" + " .");
			}
			else
			{
				System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Could not make resume file!");
			}
			
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": User quit before file completion.");
		}
		else
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": What happen?  Somebody set up us the bomb.");
		}
		
		
		
		// END of SECTION: Cleanup
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
				{
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Connection closed (gracefully).");
					return false;
				}
				
				p.bytesLeft += l;
				p.readBuffer.position(0);
			}
			catch(IOException e)
			{
				return false;
			}
		}
		
		if (p.blockRequest != null && p.blockRequest.status == BlockRequest.STARTED)
		{
			int tempPiece = p.blockRequest.piece;
			int tempOffset = p.blockRequest.offset;
			int tempLength = p.blockRequest.length;
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
				if(p.numRequestsCompletedThisRound == p.myMaxRequests && p.myMaxRequests < BitTortoise.MAX_OUTSTANDING_REQUESTS)
				{
					p.myMaxRequests *= BitTortoise.OUTSTANDING_REQUEST_RATE;
				}
				
				p.emptyFinishedRequests();
				p.fill(BitTortoise.completedPieces, BitTortoise.inProgress, BitTortoise.rarity);
				
				if(BitTortoise.verbose)
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Piece (" + tempPiece + "," + tempOffset + "," + tempLength + ") message (end).");
			}
		}
		
		if(!p.handshake_received)
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
				if(!Arrays.equals(external_info_hash,p.info_hash))
				{
					// Peer requested connection for a bad info hash - Throw out connection ?
					return false;
				}
				
				p.handshake_received = true;
				
				if(BitTortoise.verbose)
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Handshake message.");
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
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Keep-alive message.");
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
					p.cleanup();
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Choke message.");
				}
				else if(id == 1)
				{
					// Un-choke Message Received:
					
					// Handle un-choke message:
					p.peer_choking = false;
					
					p.emptyFinishedRequests();
					p.fill(BitTortoise.completedPieces,BitTortoise.inProgress, BitTortoise.rarity);
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Unchoke message.");
				}
				else if(id == 2)
				{
					// Interested Message Received:
					
					// Handle Interested message:
					p.peer_interested = true;
					
					if(numUnchoked < BitTortoise.NUM_TO_UNCHOKE)
						BitTortoise.unchokePeers();
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
					
					if(BitTortoise.verbose)
						System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Interested message.");
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
						System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Not Interested message.");
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
						
						// Updated so that rarity is properly preserved for bad have messages
						if(!p.completedPieces.get(piece_index))
						{
							p.completedPieces.set(piece_index, true);
							
							if(outstandingPieces.containsKey(piece_index))
							{
								outstandingPieces.get(piece_index).commonality++;
							}
						}
						
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
							System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Have (" + piece_index + ") message.");
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
						
						// This is changed so that if a client (incorrectly) sends us a new bitfield, we can preserve the correct counts
						BitSet previousReport = (BitSet)p.completedPieces.clone();
						p.completedPieces = BitTortoise.bitSetFromByteArray(ba);
						BitSet newReport = (BitSet)p.completedPieces.clone();
						
						newReport.andNot(previousReport);
						
						//update rarity of pieces
						int i = newReport.nextSetBit(0);
						while(i>0)
						{
							if(outstandingPieces.containsKey(i))
							{
								outstandingPieces.get(i).commonality++;
							}
							i = newReport.nextSetBit(i+1);
						}
						
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
							System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Bitfield message.");
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
						
						if(!BitTortoise.completedPieces.get(request_index))
							return false;
						
						if(request_begin < 0 || request_begin > BitTortoise.torrentFile.piece_length || request_length <= 0 || request_begin + request_length > BitTortoise.torrentFile.piece_length)
							return false;
						
						if(request_index == BitTortoise.totalPieceCount - 1)
						{
							if(BitTortoise.torrentFile.piece_length * request_index + request_begin + request_length > BitTortoise.torrentFile.file_length)
								return false;
						}
						
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
							System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Request (" + request_index + "," + request_begin + "," + request_length + ") message.");
					}
				}
				else if(id == 7)
				{
					if(p.bytesLeft < 13)
					{
						cont = false;
					}
					else
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
								p.blockRequest = br;
								break;
							}
						}
						if(!requestExists)
							return false;
						
						//p.blockRequest = BitTortoise.outstandingPieces.get(piece_index).getBlock(block_begin);
						p.blockRequest.status = BlockRequest.STARTED;
						
						//convert bytebuffer into byte array and store it in 
						int amountToTransfer = Math.min(p.bytesLeft - 13, length - 9);
						byte [] block = new byte[amountToTransfer];
						p.readBuffer.position(13);
						p.readBuffer.get(block, 0, amountToTransfer);
						processPieceMessage(p, piece_index, block_begin, block);
						
						// Note: the following should really be done within "processPieceMessage" instead of here, but whatever:
						// Perform state cleanup:
						int amountToTrim = amountToTransfer + 13;
						p.readBuffer.position(amountToTrim);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= (amountToTrim);
						
						if(BitTortoise.verbose)
							System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Piece (" + piece_index + "," + block_begin + "," + block_length + ") message (beginning).");
						
						// If we have already finished receiving this Piece message:
						if(p.blockRequest == null)
						{
							if(p.numRequestsCompletedThisRound == p.myMaxRequests && p.myMaxRequests < BitTortoise.MAX_OUTSTANDING_REQUESTS)
							{
								p.myMaxRequests *= BitTortoise.OUTSTANDING_REQUEST_RATE;
							}
							
							p.emptyFinishedRequests();
							p.fill(BitTortoise.completedPieces, BitTortoise.inProgress, BitTortoise.rarity);
							
							if(BitTortoise.verbose)
								System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Piece (" + piece_index + "," + block_begin + "," + block_length + ") message (end).");
						}
					}
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
							System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Cancel (" + cancel_index + "," + cancel_begin + "," + cancel_length + ") message.");
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
							System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Received Unknown message - Ignored.");
					}
				}
			}
			else if(length < 0 || length > BitTortoise.block_length)
			{
				System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": (" + p.ip + ":" + p.port + "): Disconnecting from peer - Received bad data");
				return false;
			}
			else
			{
				cont = false;
			}
		}
		
		if(p.bytesLeft >= 0)
		{
			p.readBuffer.position(p.bytesLeft);
		}
		
		return true;
	}
	
	public static byte[] getPiece(int index, int begin, int length)
	{
		long fileOffset = (index * ((long)BitTortoise.torrentFile.piece_length)) + begin;
		byte[] byteArray = new byte[length];
		try
		{
			BitTortoise.destinationFile.seek(fileOffset);
			BitTortoise.destinationFile.read(byteArray, 0, length);
		}
		catch(IOException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Error occurred while getting Piece " + index + ".");
		}
		return MessageLibrary.getPieceMessage(index, begin, length, byteArray);
	}
	
	public static boolean storePiece(Peer p, int piece_index, int piece_begin, byte [] block)
	{
		long fileOffset = (piece_index * ((long)torrentFile.piece_length)) + piece_begin + p.blockRequest.bytesRead;
		try
		{
			destinationFile.seek(fileOffset);
			destinationFile.write(block, 0, block.length);
		}
		catch(IOException e)
		{
			System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Error occurred while storing Piece " + piece_index + ".");
			return false;
		}
		
		return true;
	}
	
	public static boolean processPieceMessage(Peer p, int piece_index, int block_begin, byte[] block)
	{
		// Update the number of bytes read this round for this peer:
		p.bytesReadThisRound += block.length;
		BitTortoise.totalDownloaded += block.length;
		
		// Update the last time modified:
		p.blockRequest.timeModified = (new Date()).getTime();
		
		// Do other stuff (by KENNY!):
		if(!storePiece(p, piece_index, block_begin, block))
		{
			return false;
		}
		p.blockRequest.bytesRead += block.length;
		if(p.blockRequest.bytesRead >= p.blockRequest.length) //if done reading block
		{
			p.blockRequest.status = BlockRequest.FINISHED;
			p.blockRequest = null; //this peer is open to receive a new block
			if(outstandingPieces.get(piece_index).allFinished())
			{
				if(Arrays.equals(BitTortoise.getSha1FromFile(piece_index, BitTortoise.totalPieceCount, BitTortoise.destinationFile, BitTortoise.torrentFile), (byte[])BitTortoise.torrentFile.piece_hash_values_as_binary.get(piece_index)))
				{
					Piece temp = BitTortoise.outstandingPieces.remove(piece_index);
					BitTortoise.rarity.remove(temp);
					BitTortoise.completedPieces.set(piece_index);
					if (BitTortoise.outstandingPieces.isEmpty())
					{
						isIncomplete = false;
					}
					// The piece has been finished:
					BitTortoise.inProgress.set(piece_index, false);
					BitTortoise.completedPieces.set(piece_index, true);
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Completed piece " + piece_index);
				}
				else
				{
					System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())) + ": Error in SHA1 hash for piece " + piece_index + "!");
					BitTortoise.outstandingPieces.get(piece_index).resetAll();
				}
			}
		}
		return true;
	}
	
	public static byte[] getSha1FromFile(int index, int pieceCount, RandomAccessFile raf, TorrentFile tf)
	{
		byte[] entirePiece;
		if(index == pieceCount - 1)
			entirePiece = new byte[tf.file_length - (index) * tf.piece_length]; //this is a HUGE array, is there a better way to do this?
		else
			entirePiece = new byte[tf.piece_length]; //this is a HUGE array, is there a better way to do this?
		byte[] mySHA1;
		try
		{
			destinationFile.seek(index * ((long)tf.piece_length));
			if(index == pieceCount - 1)
			{
				raf.read(entirePiece, 0, tf.file_length - (pieceCount - 1) * tf.piece_length);
			}
			else
			{
				raf.read(entirePiece, 0, tf.piece_length);
			}
		}
		catch(Exception e)
		{
			System.out.println("Error reading in entire piece");
			System.exit(1);
		}
		mySHA1 = SHA1Functions.getSha1Hash(entirePiece);
		
		return mySHA1;
	}
	
	public static void printStatus()
	{
		for(int i = 0; i < BitTortoise.totalPieceCount; i += 25)
		{
			for(int j = 0; (j < 25) && (i + j < BitTortoise.totalPieceCount); j ++)
			{
				System.out.print((BitTortoise.completedPieces.get(i + j)? "*" : "." ));
			}
			System.out.println();
		}
		System.out.println("Received: " + BitTortoise.totalDownloaded + " bytes of file data.");
		System.out.println("Sent: " + BitTortoise.totalUploaded + " bytes of file data.");
	}
	
	public static void unchokePeers()
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
		if(possiblePeers.size() > NUM_TO_UNCHOKE)
		{
			// Unchoke the top 3 peers that are sending us stuff, regardless... also make sure that there are (top) 3 interested peers unchoked
			int interested = 0;
			for(int i = 0; i < NUM_TO_UNCHOKE; i ++)
			{
				if(possiblePeers.get(i).peer_interested)
					interested ++;
				possiblePeers.get(i).shouldUnchoke = true;
			}
			
			numUnchoked = NUM_TO_UNCHOKE;
			
			int index = NUM_TO_UNCHOKE;
			while(interested < NUM_TO_UNCHOKE && index < possiblePeers.size())
			{
				if(possiblePeers.get(index).peer_interested)
				{
					numUnchoked ++;
					possiblePeers.get(index).shouldUnchoke = true;
					interested ++;
				}
				index ++;
			}
			
			// get one to randomly unchoke
			int optimisticUnchokeIndex = (int)(Math.random() * (possiblePeers.size() - NUM_TO_UNCHOKE));
			optimisticUnchokeIndex = optimisticUnchokeIndex + NUM_TO_UNCHOKE;
			if(optimisticUnchokeIndex > 0 && optimisticUnchokeIndex < possiblePeers.size() && !possiblePeers.get(optimisticUnchokeIndex).shouldUnchoke && possiblePeers.get(optimisticUnchokeIndex).am_choking)
				possiblePeers.get(optimisticUnchokeIndex).shouldUnchoke = true;
		}
		else
		{
			numUnchoked = 0;
			for(Peer p : possiblePeers)
			{
				numUnchoked ++;
				p.shouldUnchoke = true;
			}
		}
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
			
			if(p.am_choking == false && p.shouldUnchoke == true)
			{
				p.shouldUnchoke = false;
			}
			
			if(p.am_choking == true  && p.shouldChoke == true)
			{
				p.shouldChoke = false;
			}
			p.finalizeRound();
		}
		startT = new Date().getTime();
	}
}
