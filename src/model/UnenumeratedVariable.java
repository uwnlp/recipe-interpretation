package model;

public class UnenumeratedVariable extends Variable {

	public UnenumeratedVariable(String name) {
		super(name);
	}

	@Override
	public int numValues() {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public Value getValue(int index) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public int getValueIndex(Value value) throws Exception {
		// TODO Auto-generated method stub
		return 0;
	}

	
}
