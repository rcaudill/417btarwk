
public class Message {
	MessageTypes theType;

	/**
	 * Creates Message Object. Given a byte[] it translates what it is. 
	 * 
	 * @param buffer - message received from a peer
	 * @param numRead - the number of bytes read
	 */
	public Message(byte[] buffer, int numRead){
		if(buffer[0] == 19 && numRead == 68){
			theType = MessageTypes.HANDSHAKE;
			return;
		}
		
		if(buffer[0] == 0 && buffer[1] == 0 && buffer[2] == 0 && buffer[3] == 0){
			theType = MessageTypes.KEEPALIVE;
			return;
		}
		
		int index = 4;
		
		if(buffer[0] == 19)
			index = 72;
		
		switch(buffer[index]){
		case 0:
			theType = MessageTypes.CHOKE;
			break;
		case 1:
			theType = MessageTypes.UNCHOKE;
			break;
		case 2:
			theType = MessageTypes.INTERESTED;
			break;
		case 3:
			theType = MessageTypes.NOTINTERESTED;
			break;
		case 4:
			theType = MessageTypes.HAVE;
			break;
		case 5:
			theType = MessageTypes.BITFIELD;
			break;
		case 6:
			theType = MessageTypes.REQUEST;
			 break;
		case 7:
			theType = MessageTypes.PIECE;
			break;
		case 8:
			theType = MessageTypes.CANCEL;
			break;
		case 9:
			theType = MessageTypes.PORT;
			break;
		default:
			theType = MessageTypes.ERROR;
		}
			
	}
}
