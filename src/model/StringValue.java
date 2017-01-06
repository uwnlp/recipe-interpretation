package model;

import edu.stanford.nlp.util.StringUtils;

/**
 * Value class where the value stored is a tokenized text string.
 * 
 * @author chloe
 *
 */
public class StringValue implements Value, Comparable<Value> {

	private String[] tokens_;
	
	public StringValue(String str) {
		tokens_ = str.split(" ");
	}
	
	public int numTokens() {
		return tokens_.length;
	}
	
	public String getToken(int i) {
		return tokens_[i];
	}
	
	@Override
	public int compareTo(Value other) {
		return toString().compareTo(other.toString());
	}
	
	public String toString() {
		return StringUtils.join(tokens_);
	}

}
