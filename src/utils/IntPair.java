package utils;

/*
 * Basic pair utility class.
 */
public class IntPair implements Comparable<IntPair >{
	private int first;
	private int second;
	
	public IntPair(int first, int second) {
		this.first = first;
		this.second = second;
	}
	
	public int getFirst() {
		return first;
	}
	
	public int getSecond() {
		return second;
	}
	
	public int compareTo(IntPair other) {
		return toString().compareTo(other.toString());
	}
	
	public boolean equals(Object other) {
		return compareTo((IntPair)other) == 0;
	}
	
	public IntPair sortedPair() {
		if (first < second) {
			return this;
		}
		return new IntPair(second, first);
	}
	
	public IntPair reversePair() {
		return new IntPair(second, first);
	}
	
	public int hashCode() {return toString().hashCode();}
	
	public String toString() {
		return "<" + first + " , " + second + ">";
	}

	public int[] toArray() {
		return new int[] {first, second}; 
	}
}
