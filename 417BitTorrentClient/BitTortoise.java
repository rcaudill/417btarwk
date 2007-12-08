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
	private static final int MAX_OUTSTANDING_REQUESTS = 5;
	
	private static TorrentFile torrentFile; // the object into which the .torrent file is b-decoded
	private static RandomAccessFile destinationFile; // The file into which we are writing
	private static Map<Integer, Piece> outstandingPieces = new HashMap<Integer, Piece>();
	private static int block_length = 16384; //The reality is near all clients will now use 2^14 (16KB) requests. Due to clients that enforce that size, it is recommended that implementations make requests of that size. (TheoryOrg spec)
	private static BitSet completedPieces; // Whether the Pieces/blocks of the file are completed or not
	
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
		int totalPieceCount = 0;
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
		
		/* initialize all block requests for transfer */
		for (int i=0; i < totalPieceCount; i++) {
			outstandingPieces.put(new Integer(i), new Piece(i));
			for (int j=0; j < torrentFile.piece_length / block_length; j++) {
				outstandingPieces.get(i).addBlock(j * block_length, block_length);
			}
		}
		
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
				float elapsedTimeSec = (finish.getTime() - start.getTime())/1000F;
			    /*if ten seconds has past, reorder the top peers get a new one to opt unchoke.*/
			    if(elapsedTimeSec % 10 == 0){
			    	ArrayList<Peer> possiblePeers = new ArrayList<Peer>();
			    	for(Map.Entry<SocketChannel, Peer> e : activePeerMap.entrySet()){
			    		if(e.getValue() != null){
			    			possiblePeers.add(e.getValue());
			    		}
			    	}
			    	
			    	/*sorts it based on bytesReadThisRound*/
			    	Collections.sort(possiblePeers, new topThreeComparator());
			    	possiblePeers.get(0).shouldUnchoke=true;
			    	possiblePeers.get(1).shouldUnchoke=true;
			    	possiblePeers.get(2).shouldUnchoke=true;
			    	
			    	/*get one to randomly unchoke*/
			    	int optimisticUnchokeIndex = (int)((Math.random() * possiblePeers.size()) + 3); 
			    	possiblePeers.get(optimisticUnchokeIndex).shouldUnchoke=true;
			    	
			    	/*go through and set the peers as choked if they aren't already*/
			    	for(int j=0;j<possiblePeers.size();j++){
			    		if(possiblePeers.get(j).am_choking == false &&
			    				possiblePeers.get(j).shouldUnchoke == false){
			    			possiblePeers.get(j).shouldChoke = true;
			    		}
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
										
										numConnections ++;
									}
									catch(IOException e)
									{
										System.err.println("Could not open new connection to peer - " + e.getMessage());
										
										if(activePeerMap.containsValue(p))
										{
											removePeer(p, activePeerMap);
										}
										key.cancel();
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
										}
										
										if(size != 0)
										{
											if(!readAndProcess(activePeerMap.get(sc), sc, false))
											{
												key.cancel();
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
									writablePeer.sendMessage(sc, completedPieces);
								}
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
				p.bytesLeft += socketChannel.read(p.readBuffer);
				p.readBuffer.position(0);
			}
			catch(IOException e)
			{
				return false;
			}
		}
		
		if (p.blockRequest != null && p.blockRequest.status == BlockRequest.STARTED) {
			byte[] block;
			int bytesLeftInBlock = p.blockRequest.length - p.blockRequest.bytesRead;
			if (p.bytesLeft <= bytesLeftInBlock) {
				block = new byte[p.bytesLeft];
				p.readBuffer.get(block, 0, p.bytesLeft);
				p.readBuffer.clear();
				p.bytesLeft = 0;
			}
			else {
				block = new byte[bytesLeftInBlock];
				p.readBuffer.get(block, 0, bytesLeftInBlock);
				p.readBuffer.position(bytesLeftInBlock);
				p.readBuffer.compact();
				p.readBuffer.position(0);
				p.bytesLeft -= bytesLeftInBlock;
			}
			processPieceMessage(p, p.blockRequest.piece, p.blockRequest.offset, p.blockRequest.length, block);
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
			}
			else if(length >= 1 && p.bytesLeft >= 5)
			{
				byte id = p.readBuffer.get(4);
				if(id == 0)
				{
					// Choke Message Received:
					
					// Handle choke message:
					p.peer_choking = true;
					
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
				}
				else if(id == 1)
				{
					// Un-choke Message Received:
					
					// Handle un-choke message:
					p.peer_choking = false;
					BitSet choices = p.completedPieces;
					
					// loops and find the first open piece - This could be much better
					for(int i=choices.nextSetBit(0); i>=0; i=choices.nextSetBit(i+1))
					{
						if(completedPieces.get(i) == false)
						{
							for(BlockRequest br : outstandingPieces.get(i).blocks)
							{
								if(p.sendRequests.size() == MAX_OUTSTANDING_REQUESTS)
									break;
								if(br.status == BlockRequest.UNASSIGNED)
								{
									br.status = BlockRequest.UNREQUESTED;
									p.sendRequests.add(br);
								}
							}
							break;
						}
					}
					// Perform state cleanup:
					p.readBuffer.position(5);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= 5;
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
						
						// More
						
						// Perform state cleanup:
						p.readBuffer.position(9);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= 9;
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
						
						// More??
						
						// Perform state cleanup:
						p.readBuffer.position(length + 4);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= (length + 4);
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
					
					p.blockRequest = outstandingPieces.get(piece_index).getBlock(block_begin);
					p.blockRequest.status = BlockRequest.STARTED;
					
					//convert bytebuffer into byte array and store it in 
					byte [] block = new byte[p.bytesLeft - 13];
					p.readBuffer.get(block, 10, p.bytesLeft - 13);
					processPieceMessage(p, piece_index, block_begin, block_length, block);
					
					// Perform state cleanup:
					p.readBuffer.position(length + 4);
					p.readBuffer.compact();
					p.readBuffer.position(0);
					
					p.bytesLeft -= (length + 4);
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
						
						// Remove the piece with the
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
					}
				}
				else
				{
					// Unrecognized id, ignore the rest of length bytes
					if(p.bytesLeft < length + 4)
					{
						cont = false;
					}
					else
					{
						p.readBuffer.position(length + 4);
						p.readBuffer.compact();
						p.readBuffer.position(0);
						
						p.bytesLeft -= (length + 4);
					}
				}
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
			destinationFile.read(byteArray, fileOffset, length);
		}
		catch(IOException e)
		{
			System.err.println("Error occurred while getting Piece " + index + ".");
		}
		return MessageLibrary.getPieceMessage(index, begin, length, byteArray);
	}
	
	public static boolean storePiece(Peer p, int piece_index, int piece_begin, int piece_length, byte [] block) {
		int fileOffset = (piece_index * torrentFile.piece_length) + piece_begin + p.blockRequest.bytesRead; 
		try
		{
			destinationFile.write(block, fileOffset, piece_length);
		}
		catch(IOException e)
		{
			System.err.println("Error occurred while storing Piece " + piece_index + ".");
			return false;
		}
		
		return true;
	}
	
	public static boolean processPieceMessage(Peer p, int piece_index, int block_begin, int block_length, byte[] block) {
		if (!storePiece(p, piece_index, block_begin, block_length, block)) {
			return false;
		}
		p.blockRequest.bytesRead += block.length;
		if (p.blockRequest.bytesRead >= p.blockRequest.length) { //if done reading block
			p.blockRequest.status = BlockRequest.FINISHED;
			p.blockRequest = null;
			if (outstandingPieces.get(piece_index).allFinished()) {
				byte [] entirePiece = new byte [torrentFile.piece_length]; //this is a HUGE array, is there a better way to do this?
				byte [] mySHA1;
				try {
					destinationFile.read(entirePiece, piece_index * torrentFile.piece_length, torrentFile.piece_length);
				} catch (Exception e) {
					System.out.println("error reading in entire piece");
					System.exit(1);
				}
				mySHA1 = SHA1Functions.getSha1Hash(entirePiece);
				if (mySHA1.equals(torrentFile.piece_hash_values_as_binary.get(piece_index))) {
					outstandingPieces.remove(piece_index);
					//update bitset
					if (outstandingPieces.isEmpty()) {
						System.out.println("received entire file... do something");
					}
				}
				else {
					//reset all blocks to empty and bytesread to 0
				}
			}
		}
		return true;
	}
}
