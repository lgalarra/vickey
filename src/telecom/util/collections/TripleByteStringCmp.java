package telecom.util.collections;

import java.util.Comparator;

import javatools.datatypes.ByteString;
import javatools.datatypes.Triple;

public class TripleByteStringCmp implements Comparator<Triple<ByteString, ByteString, ByteString>> {

	private ByteStringCmp bscmp = new ByteStringCmp();
	
	@Override
	public int compare(Triple<ByteString, ByteString, ByteString> o1,
			Triple<ByteString, ByteString, ByteString> o2) {
		int sb = bscmp.compare(o1.first, o2.first); 
		if (sb == 0) {
			int rb = bscmp.compare(o1.second, o2.second);
			if (rb == 0) {
				return bscmp.compare(o1.third, o2.third);
			} else {
				return rb;
			}
		} else {
			return sb;
		}
	}
}
