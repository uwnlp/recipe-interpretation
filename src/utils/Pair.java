package utils;


import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/*
 * Basic pair utility class.
 */
public class Pair<X, Y> implements Comparable<Pair<X, Y> >, Serializable {
	private X first;
	private Y second;

	public Pair(X first, Y second) {
		this.first = first;
		this.second = second;
	}

	public X getFirst() {
		return first;
	}

	public Y getSecond() {
		return second;
	}

	@SuppressWarnings("unchecked")
	public int compareTo(Pair<X, Y> other) {
		int comp = 0;
		if (first == null) {
			if (other.getFirst() != null) {
				return -1;
			} else {
				comp = 0;
			}
		} else {
			if (other.getFirst() == null) {
				return 1;
			} else {
				comp = ((Comparable<X>) first).compareTo(other.first);
			}
		}

		if (comp != 0) {
			return comp;
		} else {
			return ((Comparable<Y>)second).compareTo(other.second);
		}
	}

	@SuppressWarnings("unchecked")
	public boolean equals(Object other) {
		return compareTo((Pair<X, Y>)other) == 0;
	}

	public int hashCode() {return toString().hashCode();}

	public String toString() {
		return "<" + first + " , " + second + ">";
	}
}
