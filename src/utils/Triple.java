package utils;

import java.io.Serializable;


public class Triple<X,Y,Z> implements Comparable<Triple<X,Y,Z>>, Serializable {
	private X first_;
	private Y second_;
	private Z third_;
	public Triple(X first, Y second, Z third) {
		first_=first; second_=second; third_ = third;	
	}
	public X getFirst() {return first_;}
	public Y getSecond() {return second_;}
	public Z getThird() {return third_;}
	public void setFirst(X first) {first_=first;}
	public void setSecond(Y second) {second_=second;}
	public void setThird(Z third) {third_=third;}
	
	public int compareTo(Triple<X,Y,Z> z) {		
		int rst=((Comparable)first_).compareTo(z.first_);
		if (rst!=0) return rst;
		rst = ((Comparable)second_).compareTo(z.second_);
		if (rst!=0) return rst;
		else return ((Comparable)third_).compareTo(z.third_);
	}
	public boolean equals(Object o) {return compareTo((Triple<X,Y,Z>)o)==0;}
	public int hashCode() {return toString().hashCode();}
	public String toString() {return "<"+first_+" , "+second_+" , " + third_ + ">";}
}
