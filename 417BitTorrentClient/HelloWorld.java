import java.io.RandomAccessFile;
import java.nio.*;
import java.nio.channels.*;
import java.util.regex.*;
import java.util.*;
import java.text.*;


public class HelloWorld {
	public static void main(String [] args) {
		//System.out.println("hello world. Accept: " + SelectionKey.OP_ACCEPT + ", Connect: " + SelectionKey.OP_CONNECT + ", Write: " + SelectionKey.OP_WRITE + ", Read: " + SelectionKey.OP_READ);
		/*
		int piece_index = -1189909828;
		long piece_index_long = ((long)piece_index) & 0x00000000FFFFFFFF;
		System.out.println(piece_index_long);
		System.out.println(Integer.toHexString(piece_index));
		
		System.out.println(Long.parseLong("00000000b9136abc", 16));
		*/
		/*
		// Handshake Message Received:
		byte[] bytes = (new String("" + ((char)19) + "BitTorrent protocol" + new String(new byte[]{0,0,0,0,0,0,0,0}) + new String(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,14,13,12,11,1}) + new String(new byte[]{15,14,13,12,11,10,9,8,7,6,5,4,3,2,1,0,1,2,3,4}) + new String(new byte[]{0,0,0,1,3}))).getBytes();
		ByteBuffer b = ByteBuffer.allocate(100);
		
		b.put(bytes);
		
		byte[] external_info_hash = new byte[20];
		byte[] external_peer_id = new byte[20];
		
		b.position(28);
		b.get(external_info_hash, 0, 20);
		
		b.position(48);
		b.get(external_peer_id, 0, 20);
		
		System.out.println(Peer.getBytesAsHex(external_info_hash));
		System.out.println(Peer.getBytesAsHex(external_peer_id));
		
		b.position(68);
		b.compact();
		b.position(0);
		System.out.println(b.remaining());
		
		System.out.println(b.position());
		System.out.println(b.getInt(0));
		System.out.println(b.get(4));
		
		System.out.println("" + (byte)b.get(0) + "       hello -" + b.array()[0] + " " + ByteBuffer.wrap(external_info_hash).equals(ByteBuffer.wrap(new byte[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,14,13,12,11,1})));
		*/
		/*
		ByteBuffer have = ByteBuffer.allocate(17);
		have.put(new byte[]{0,0,0,13,6});
		have.putInt(4);
		have.putInt(5);
		have.putInt(6);
		byte[] h = have.array();
		String result = "";
		for(byte b : h)
			result += (b + ", ");
		System.out.println(result.substring(0, result.lastIndexOf(", ")));
		*/
		/*
		byte[] my_peer_id = new byte[20];
		my_peer_id[0] = (byte)'-';// Replace the beginning of the id with "-BT0001-" to mimic normal naming schemes 
		my_peer_id[1] = (byte)'B'; 
		my_peer_id[2] = (byte)'T'; 
		my_peer_id[3] = (byte)'0'; 
		my_peer_id[4] = (byte)'0'; 
		my_peer_id[5] = (byte)'0'; 
		my_peer_id[6] = (byte)'1'; 
		my_peer_id[7] = (byte)'-';
		for(int i = 8; i < my_peer_id.length; i ++)
			my_peer_id[i] = (byte)((Math.random() * 0x5F) + 0x20);
		
		System.out.println(new String(my_peer_id));*/
		
		//checkEquality("wrar361.2.exe","wrar361.2.exe");
		
		String s = "Piece: 25 Offset: 30026 Length: 173656";
		String regex = "Piece: (-?[0-9]+) Offset: (-?[0-9]+) Length: (-?[0-9]+)";
		Pattern p = Pattern.compile(regex);
		Matcher m = p.matcher(s);
		if(m.matches())
		{
			System.out.println(""+ Integer.parseInt(m.group(1)) + " "+Integer.parseInt(m.group(2))+" "+Integer.parseInt(m.group(3)));
		}
		else
		{
			System.out.println("no match");
		}
		
		System.out.println(((new SimpleDateFormat("[kk:mm:ss]")).format(new Date())));
	}
	
	public static boolean checkEquality(String filename1, String filename2)
	{
		try
		{
			RandomAccessFile raf1 = new RandomAccessFile(filename1,"rw");
			RandomAccessFile raf2 = new RandomAccessFile(filename2,"rw");
			
			if(raf1.length() != raf2.length())
			{
				System.out.println("Files are of different length.");
				return false;
			}
			
			long length = raf1.length();
			
			byte[] arr1 = new byte[1024];
			byte[] arr2 = new byte[1024];
			
			for(long l = 0; l < length; l += 1024)
			{
				raf1.seek(l);
				raf2.seek(l);
				
				raf1.read(arr1);
				raf2.read(arr2);
				
				if(!ByteBuffer.wrap(arr1).equals(ByteBuffer.wrap(arr2)))
				{
					System.out.println("Files differ in byte range " + l + " - " + (l + 1024) + " of " + length + ".");
					return false;
				}
			}
		}
		catch(Exception e)
		{
			System.err.println("File read/comparison error " + e.getMessage());
			return false;
		}
		
		System.out.println("Files are equal!");
		return true;
	}
}
