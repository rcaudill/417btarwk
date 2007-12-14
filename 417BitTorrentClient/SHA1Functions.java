import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SHA1Functions {
	
	public static void main(String[] args) {
		try{
		byte[] theText = "The Test".getBytes("8859_1");
		getSha1Hash(theText);
		}catch(UnsupportedEncodingException e){
			System.out.println(e);
		}
	}
	
	public static byte[] getSha1Hash(byte[] pieceBytes) {
		byte[] digest = null;
		MessageDigest md;
		try{
			md = MessageDigest.getInstance("SHA");
	        md.update(pieceBytes);
	        digest = md.digest();
	           
		}catch(NoSuchAlgorithmException e){
			System.exit(1);
		}
		return digest;
	}
	
	public static void printSha1HashAsHex(byte[] pieceBytes) {
		for(byte b: pieceBytes){
	    	   System.out.print(Integer.toHexString(b & 0xff) + " ");
	    }
	}
}
