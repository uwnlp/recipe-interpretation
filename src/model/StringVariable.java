package model;

import java.util.ArrayList;
import java.util.List;

/**
 * StringVariable is a subclass of Variable where the domain consists of StringValues.
 * 
 * @author chloe
 *
 */
public class StringVariable extends Variable {
	
	private List<StringValue> values_;
	
	public StringVariable(String name, List<StringValue> values, boolean deep_copy) {
		super(name);
		if (deep_copy) {
			values_ = new ArrayList<StringValue>(values);
		} else {
			values_ = values;
		}
	}
	
	public int numValues() {
		return values_.size();
	}

	public Value getValue(int index) {
		return values_.get(index);
	}

	public int getValueIndex(Value value) throws Exception {
		for (int i = 0; i < values_.size(); i++) {
			if (values_.get(i).equals(value)) {
				return i;
			}
		}
		throw new Exception("Value " + value + " is not a value of variable " + name());
	}

}
