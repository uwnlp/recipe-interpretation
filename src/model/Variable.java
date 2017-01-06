package model;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple variable class.
 * 
 * Variable has a String name and a set of possible values.
 * 
 * Variables can be set to a particular value from the set
 * of possible variables, and un-set.
 * 
 * @author chloe
 *
 */
public abstract class Variable implements Comparable<Variable> {

	private String name_;
	
	// Allows us to fix the variable to a particular value
	private boolean is_set_;
	private Value set_value_;
	
	public Variable(String name) {
		name_ = name;
		is_set_ = false;
		set_value_ = null;
	}
	
	public String name() {
		return name_;
	}
	
	public abstract int numValues();

	public abstract Value getValue(int index);

	public abstract int getValueIndex(Value value) throws Exception;
	
	// Returns true if the Variable is set to a particular value.
	public boolean isSet() {
		return is_set_;
	}
	
	// Returns the Value that a variable is set to. This Value should
	// be null if the Variable is un-set.
	public Value getSetValue() {
		return set_value_;
	}

	public void setValue(Value value) throws Exception {
		if (isSet()) {
			throw new Exception("Variable " + name() + " is already set.");
		}
		is_set_ = true;
		set_value_ = value;
	}
	
	public void unsetValue() {
		is_set_ = false;
		set_value_ = null;
	}

	public int compareTo(Variable arg0) {
		return toString().compareTo(arg0.toString());
	}
	
	public boolean equals(Object obj) {
		if (obj instanceof Variable) {
			return compareTo((Variable)obj) == 0;
		}
		return false;
	}
	
	public String toString() {
		if (!is_set_) {
			return name_;
		} else {
			return name_ + ":" + set_value_.toString();
		}
	}
	
	public int hashCode() {
		return name_.hashCode();
	}
}
