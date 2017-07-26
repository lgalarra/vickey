package telecom.util.datatypes;


public class BigBinaryNumber implements Cloneable {
	
	private final BitSet data;
	
	private int fixedBits = 0;

	/**
	 * 
	 * @param number
	 */
	public BigBinaryNumber(String numberStr) {
		data = new BitSet(numberStr.length());
		for (int i = 0; i < data.length(); ++i) {
			char c = numberStr.charAt(i);
			if (c != '0' && c != '1')
				throw new IllegalArgumentException("BigBinaryNumber expects a string containing only 1s or 0s");
			data.set(i, c == '1');
		}
	}
	
	public BigBinaryNumber(int size) {
		data = new BitSet(size);
	}
	
	private BigBinaryNumber(BitSet data) {
		this.data = data;
	}
	
	/**
	 * Returns the highest position containing a 1
	 * @return -1 if there are not 1s in the number
	 */
	private int highest(boolean val) {
		for (int i = data.length() - fixedBits - 1; i >= 0; --i) {
			if (data.get(i) == val) {
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * Returns the highest position containing a 1
	 * @return -1 if there are not 1s in the number
	 */
	private int lowest(boolean val) {
		for (int i = 0; i < data.length() - fixedBits; ++i) {
			if (data.get(i) == val) {
				return i;
			}
		}
		
		return -1;
	}
	
	/**
	 * It adds 1 to the value of the number
	 * @return the same object modified or null if the operation did 
	 * not succeded (the number stores the highest possible value that can be represented
	 * with the given number of bits)
	 */
	public BigBinaryNumber increment() {
		int highestOne = highest(true);
		int lowestZero = lowest(false);
		
		if (highestOne == data.length() - fixedBits - 1
				&& lowestZero == -1) {
			return null;
		}
		
		if (lowestZero < highestOne) {
			// Nice case
			data.set(lowestZero, true);
			for (int i = 0; i < lowestZero; ++i) {
				data.set(i, false);
			}
		} else {
			// Nice case
			data.set(lowestZero, true);
			for (int i = 0; i <= highestOne; ++i) {
				data.set(i, false);
			}
		}
		
		return this;
	}
	
	@Override
	public String toString() {
		return data.toString();
	}
	
	@Override
	public int hashCode() {
		return data.hashCode();
	}
	
	public boolean[] toArray() {
		return data.getData();
	}
	
	public int size() {
		return data.size();
	}
	
	/**
	 * Return the number of 1s in the binary number. It ignores the fixed bits
	 * @return
	 */
	public int nOnes() {
		int ones = 0;
		for (int i = 0; i < data.length() - fixedBits; ++i) {
			if (data.get(i))
				++ones;
		}
		
		return ones;
	}
	
	public static void main(String args[]) {
		BigBinaryNumber n = new BigBinaryNumber("00000000");
		n.fixBits(new BigBinaryNumber("101"));
		do {
			System.out.println(n);
		} while(n.increment() != null);
	}

	/**
	 * It fixes the first 'n' bits of the binary number to the values
	 * in the prefix.
	 * @param prefix
	 */
	public void fixBits(BigBinaryNumber prefix) {
		if (prefix.size() > size()) {
			throw new ArrayIndexOutOfBoundsException(
					"The prefix cannot be larger than the calling binary number.");
		}
		fixedBits = prefix.size();
		int index = data.size() - 1;
		for (int k = 0; k < fixedBits; ++k, --index) {
			data.set(index, prefix.data.get(k));
		}
	}
	
	@Override
	protected Object clone() {
		return (Object) new BigBinaryNumber((BitSet)data.clone());
	}
	
	/**
	 * Return a copy of this big binary number
	 * @return
	 */
	public BigBinaryNumber copy() {
		return (BigBinaryNumber) clone();
	}
}
