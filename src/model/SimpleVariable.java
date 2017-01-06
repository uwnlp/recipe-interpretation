package model;

/**
 * Variable subclass whose values are all SimpleValues.
 * 
 * @author chloe
 *
 */
public class SimpleVariable extends Variable {

	SimpleValue[] values_;
	
	public SimpleVariable(String name, SimpleValue[] values) {
		super(name);
		values_ = values;
	}

	public int numValues() {
		return values_.length;
	}

	public Value getValue(int index) {
		return values_[index];
	}

	public int getValueIndex(Value value) throws Exception {
		for (int i = 0; i < values_.length; i++) {
			if (values_[i].equals(value)) {
				return i;
			}
		}
		throw new Exception("Value " + value + " is not a value of variable " + name());
	}

}
