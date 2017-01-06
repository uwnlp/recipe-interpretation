package model;


/**
 * Simple value class.
 * 
 * Stores the value of a variable.
 * 
 * 
 * @author chloe
 *
 */
public interface Value {

	public int hashCode();
	
	public String toString();
	
	public int compareTo(Value other);
}
