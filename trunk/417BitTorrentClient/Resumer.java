import java.util.*;
import java.util.regex.*;
import java.io.*;

public class Resumer
{
	public static boolean resumeFromStopped(String resumeInfoFilename, RandomAccessFile destinationFile, TorrentFile torrentFile, Map<Integer, Piece> map, BitSet completed, BitSet inProgress, int totalPieces)
	{
		completed.set(0, totalPieces, true);
		inProgress.set(0, totalPieces, false);
		
		// Load all of the unfinished block requests from the file:
		try
		{
			File file = new File(resumeInfoFilename);
			RandomAccessFile resumeFile = new RandomAccessFile(file, "rw");
			
			if(resumeFile.length() == 0)
				return false;
			
			String s;
			while((s = resumeFile.readLine()) != null)
			{
				Piece p;
				BlockRequest br = BlockRequest.fromString(s);
				
				if(br == null)
					return false;
				
				// Destroy other state information:
				if(br.status != BlockRequest.FINISHED)
					br.status = BlockRequest.UNASSIGNED;
				
				if(map.containsKey(br.piece))
				{
					p = map.get(br.piece);
				}
				else
				{
					p = new Piece(br.piece);
					map.put(br.piece, p);
				}
				p.blocks.add(br);
				
				if(br.status != BlockRequest.UNASSIGNED)
					inProgress.set(br.piece, true);
				
				completed.set(br.piece, false);
			}
			
			resumeFile.setLength(0);
			resumeFile.close();
			file.delete();
		}
		catch(IOException e)
		{
			return false;
		}
		catch(SecurityException e) { }
		
		// Load all of the finished pieces (ones not in the map) from the destination file, check their hashes:
		try
		{
			if(destinationFile.length() != torrentFile.file_length)
				return false;
			
			// Cycle through completed pieces, check that their SHA1 hashes are equal:
			int i = 0;
			while((i = completed.nextSetBit(i)) != -1)
			{
				byte[] sha = BitTortoise.getSha1FromFile(i, totalPieces, destinationFile, torrentFile);
				if(!Arrays.equals(sha, (byte[])torrentFile.piece_hash_values_as_binary.get(i)))
				{
					return false;
				}
				i++;
			}
		}
		catch(IOException e)
		{
			return false;
		}
		
		return true;
	}
	
	public static boolean saveStatus(String resumeInfoFilename, Map<Integer, Piece> map)
	{
		String endOfLine = System.getProperty("line.separator");
		try
		{
			RandomAccessFile resumeFile = new RandomAccessFile(resumeInfoFilename, "rw");
			
			if(resumeFile.length() > 0)
				return false;
			
			for(Map.Entry<Integer, Piece> entry : map.entrySet())
			{
				Piece piece = (Piece)entry.getValue();
				for(BlockRequest br : piece.blocks)
				{
					resumeFile.writeBytes(br.toPrintString() + endOfLine);
				}
			}
			
			resumeFile.close();
		}
		catch(IOException e)
		{
			return false;
		}
		return true;
	}
	
	public static boolean checkSeed(RandomAccessFile sourceFile, TorrentFile torrentFile)
	{
		int totalPieces = ((int)torrentFile.file_length/torrentFile.piece_length) + (((torrentFile.file_length % torrentFile.piece_length) == 0)? (0) : (1));
		
		// Load all of the finished pieces (ones not in the map) from the destination file, check their hashes:
		try
		{
			if(sourceFile.length() != torrentFile.file_length)
				return false;
			
			// Cycle through completed pieces, check that their SHA1 hashes are equal:
			for(int i = 0; i < totalPieces; i++)
			{
				byte[] sha = BitTortoise.getSha1FromFile(i, totalPieces, sourceFile, torrentFile);
				if(!Arrays.equals(sha, (byte[])torrentFile.piece_hash_values_as_binary.get(i)))
				{
					return false;
				}
			}
		}
		catch(IOException e)
		{
			return false;
		}
		
		return true;
	}
}
