package amie.tests;

import javatools.datatypes.IntHashMap;
import junit.framework.TestCase;


public class IntHashMapTest extends TestCase{

	public void test1() {
		IntHashMap<String> map = new IntHashMap<String>();
		map.increase("Luis");
		map.increase("Fabian");
		map.increase("Danai");
		map.increase("Thomas");
		map.increase("JB");
		map.increase("Oana");		
		map.increase("Roxana");		
		map.increase("Lamine");
		map.increase("Ziad");
		map.increase("Antoine");		
		map.increase("Mikael");
		map.increase("Mauro");
		map.increase("Pierre");
		map.increase("JL");
		map.increase("Talel");
		map.increase("Sebastien");
		int size = map.size();
		map.remove("Antoine");
		map.remove("Thomas");		
		map.remove("JB");	
		map.remove("Talel");
		map.remove("JL");
		map.remove("Sebastien");
		map.remove("Roxana");
		map.remove("Danai");
		map.remove("Oana");		
		map.remove("Gilles");			
		assertEquals(size - 9, map.size());
		map.remove("Luis");
		map.remove("Fabian");
		map.remove("Mauro");
		assertEquals(size - 12, map.size());
		assertTrue(map.contains("Pierre"));
		assertFalse(map.contains("JB"));
		assertTrue(map.contains("Mikael"));
		assertFalse(map.contains("Antoine"));		
		map.remove("Ziad");
		map.remove("Pierre");
		map.remove("Lamine");
		map.remove("Mikael");
		assertEquals(0, map.size());
	}
	
	public void test2() {
		IntHashMap<String> map = new IntHashMap<String>();
		map.increase("<Nantes>");
		map.increase("<Paris>");
		map.increase("<France>");			
		assertEquals(3, map.size());
		map.remove("<Nantes>");
		map.remove("<Paris>");
		map.remove("<France>");			
		assertEquals(0, map.size());
	}

}
