package amie.tests;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javatools.filehandlers.TSVFile;

public class StringUtils {
	private StringUtils() {}

	public static boolean isPureAscii(String v) {
	    byte bytearray []  = v.getBytes();
	    CharsetDecoder d = StandardCharsets.US_ASCII.newDecoder();
	    try {
	      CharBuffer r = d.decode(ByteBuffer.wrap(bytearray));
	      r.toString();
	    }
	    catch(CharacterCodingException e) {
	      return false;
	    }
	    return true;
	}

	public static void main(String args[]) throws Exception {
		try (TSVFile file = new TSVFile(new File(args[0]))) {
			for (List<String> row : file) {
				if (StringUtils.isPureAscii(row.get(0)) && StringUtils.isPureAscii(row.get(2))) {
					System.out.println(row.get(0).replace(',', '_') + "\t" + row.get(1) + "\t" + row.get(2).replace(',', '_'));
				}
			}
		}
	}
}
