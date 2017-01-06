package model;

import java.util.Collection;
import java.util.Iterator;

/**
 * Simple Value class. 
 * 
 * A SimpleValue just stores a String name.
 * 
 * @author chloe
 *
 */
public class SimpleValue implements Value, Comparable<Value> {
	// Name of the variable
	private String name_;
	
	
	public SimpleValue(String event_name) {
		name_ = event_name;
	}
	
	/**
	 * Gets the name of the variable
	 * @return the String name of the variable
	 */
	public String name() {
		return name_;
	}
	
	public int compareTo(SimpleValue o) {
		return name_.compareTo(o.name_);
	}
	
	public int compareTo(Value o) {
		return toString().compareTo(o.toString());
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof Value) {
			return compareTo((Value)obj) == 0;
		}
		return false;
	}
	
	public String toString() {
		return name_;
	}
	
	public int hashCode() {
		return name_.hashCode();
	}
	
	
	public static SimpleValue[] createValueArray(Collection<String> names) {
		SimpleValue[] values = new SimpleValue[names.size()];
		Iterator<String> name_it = names.iterator();
		int n = 0;
		while (name_it.hasNext()) {
			values[n] = new SimpleValue(name_it.next());
			n++;
		}
		return values;
	}
}
