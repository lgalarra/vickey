package amie.tests;

import javatools.datatypes.ByteString;

public class ByteStringTest {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ByteString testString = ByteString.of("Arakan_Campaign_1942–43");
		System.out.println(testString.data[21]);
		System.out.println(testString);
	}

}
