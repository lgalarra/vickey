package telecom.util.datatypes;

public class BitSet implements Cloneable {
	
	private boolean[] data;
	
	public BitSet(int size) {
		data = new boolean[size];
		for (int i = 0; i < size; ++i) {
			data[i] = false;
		}
	}
	
	private BitSet(boolean[] data) {
		this.data = data;
	}
	
	public void set(int bitIndex, boolean value) {
		data[bitIndex] = value;
	}
	
	public boolean get(int bitIndex) {
		return data[bitIndex];
	}
	
	public int length() {
		return data.length;
	}
	
	public int size() {
		return data.length;
	}
	
	public boolean[] getData() {
		return data;
	}
	
	@Override
	public String toString() {
		StringBuilder strBuilder = new StringBuilder();
		for (int i = data.length - 1; i >= 0; --i) {
			strBuilder.append(data[i] ? '1' : '0');
		}
		
		return strBuilder.toString();
	}
	
	@Override
	protected Object clone() {
		boolean[] newData = data.clone();
		return (Object) new BitSet(newData);
	}
}
