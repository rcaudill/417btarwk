import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class requestAndrew {
	public static void main(String[] args) {
		try{
		byte[] theText = "The Test".getBytes("8859_1");
		getSha1(theText);
		}catch(UnsupportedEncodingException e){
			System.err.println(e);
		}
	}
	
	public static byte[] getSha1(byte[] pieceBytes) {
		byte[] digest = null;
		MessageDigest md;
		try{
			md = MessageDigest.getInstance("SHA");
	        md.update(pieceBytes);
	        digest = md.digest();
	        
	       for(byte b: digest){
	    	   System.out.print(Integer.toHexString(b & 0xff) + " ");
	       }
		}catch(NoSuchAlgorithmException e){
			System.exit(1);
		}
		return digest;
	}
}
