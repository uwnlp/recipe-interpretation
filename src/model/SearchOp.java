package model;

import model.ConnectionsModel.CONN_TYPE;
import utils.Pair;
import utils.Triple;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;

// Search operations
public class SearchOp implements Comparable<SearchOp> {
	public final static char OP_SWAP_DESTS_ = '0';
	public final static char OP_NEW_DEST_ = '1';
	public final static char OP_ADD_NEW_OUTPUT_ = '2';
	public final static char OP_REMOVE_OUTPUT_ = '3';
	public final static char OP_SPLIT_CONN_ = '4';
	public final static char OP_THREE_WAY_ = '5';
	public final static char OP_CHANGE_TYPE_ = '6';
	
	public char op_;
	
	public ActionNode origin = null;
	
	public ActionNode node1;
	public Argument arg1;
	public String span1;
	
	public ActionNode node2 = null;
	public Argument arg2;
	public String span2;
	
	public ActionNode node3 = null;
	public Argument arg3;
	public String span3;
	
	private String str = null;
	
	public boolean is_rev = false;
	
	public CONN_TYPE type_ = null;
	public CONN_TYPE new_type_ = null;
	
	public SearchOp(Triple<ActionNode, Argument, String> old_dest, Triple<ActionNode, Argument, String> new_dest,
			char op, CONN_TYPE type, GraphInfo gi) {
		this(old_dest.getFirst(), old_dest.getSecond(), old_dest.getThird(), new_dest.getFirst(), 
				new_dest.getSecond(), new_dest.getThird(), op, type, gi);
	}
	
	public SearchOp(Triple<ActionNode, Argument, String> old_dest, Triple<ActionNode, Argument, String> new_dest,
			char op, CONN_TYPE type, CONN_TYPE type2, GraphInfo gi) {
		this(old_dest.getFirst(), old_dest.getSecond(), old_dest.getThird(), new_dest.getFirst(), 
				new_dest.getSecond(), new_dest.getThird(), op, type, type2, gi);
	}
	
	public SearchOp(Triple<ActionNode, Argument, String> old_dest, Triple<ActionNode, Argument, String> new_dest,
			Triple<ActionNode, Argument, String> third,
			char op, CONN_TYPE type, GraphInfo gi) {
		this(old_dest.getFirst(), old_dest.getSecond(), old_dest.getThird(), new_dest.getFirst(), 
				new_dest.getSecond(), new_dest.getThird(), third.getFirst(), third.getSecond(), third.getThird(), 
				op, type, gi);
	}
	
	public SearchOp(Triple<ActionNode, Argument, String> old_dest, Triple<ActionNode, Argument, String> new_dest,
			Triple<ActionNode, Argument, String> third,
			char op, CONN_TYPE type, CONN_TYPE type2, GraphInfo gi) {
		this(old_dest.getFirst(), old_dest.getSecond(), old_dest.getThird(), new_dest.getFirst(), 
				new_dest.getSecond(), new_dest.getThird(), third.getFirst(), third.getSecond(), third.getThird(), 
				op, type, type2, gi);
	}
	
	public SearchOp(ActionNode node1, Argument arg1, String span1, 
			ActionNode node2, Argument arg2, String span2, char op, CONN_TYPE type, GraphInfo gi) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;
		
		op_ = op;
		type_ = type;
		genString(gi);
	}
	
	public SearchOp(ActionNode node1, Argument arg1, String span1, 
			ActionNode node2, Argument arg2, String span2, char op, CONN_TYPE type, 
			CONN_TYPE type2, GraphInfo gi) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;
		
		op_ = op;
		type_ = type;
		new_type_ = type2;
		genString(gi);
	}
	
	public SearchOp(ActionNode node1, Argument arg1, String span1, char op, CONN_TYPE type, 
			CONN_TYPE new_type, GraphInfo gi) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		op_ = op;
		type_ = type;
		new_type_ = new_type;
		
		genString(gi);
	}
	
	public SearchOp(ActionNode node1, ActionNode node2, Argument arg2, String span2, char op, CONN_TYPE type) {
		origin = node1;
		this.node1 = node2;
		this.arg1 = arg2;
		this.span1 = span2;
		
		op_ = op;
		type_ = type;
		genString(null, null, null);
	}
	
	public SearchOp(ActionNode node1, Triple<ActionNode, Argument, String> triple, char op, CONN_TYPE type) {
		origin = node1;
		this.node1 = triple.getFirst();
		this.arg1 = triple.getSecond();
		this.span1 = triple.getThird();
		
		op_ = op;
		type_ = type;
		genString(null, null, null);
	}
	
	public SearchOp(ActionNode node1, Argument arg1, String span1, ActionNode node2, Argument arg2, String span2, 
			ActionNode node3, Argument arg3, String span3, char op, CONN_TYPE type, GraphInfo gi) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;
		
		this.node3 = node3;
		this.arg3 = arg3;
		this.span3 = span3;
		
		op_ = op;
		type_ = type;
		genString(gi);
	}
	
	public SearchOp(ActionNode node1, Argument arg1, String span1, ActionNode node2, Argument arg2, String span2, 
			ActionNode node3, Argument arg3, String span3, char op, CONN_TYPE type, CONN_TYPE type2, GraphInfo gi) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;
		
		this.node3 = node3;
		this.arg3 = arg3;
		this.span3 = span3;
		
		op_ = op;
		type_ = type;
		new_type_ = type2;
		genString(gi);
	}
	
	public SearchOp(ActionNode origin1, ActionNode node1, Argument arg1, String span1, 
			ActionNode origin2, ActionNode node2, Argument arg2, String span2, char op, CONN_TYPE type) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;
		
		op_ = op;
		type_ = type;
		genString(origin1, origin2, null);
	}
	
	public SearchOp(ActionNode origin1, ActionNode node1, Argument arg1, String span1, 
			ActionNode origin2, ActionNode node2, Argument arg2, String span2, char op, 
			CONN_TYPE type, CONN_TYPE type2) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;
		
		op_ = op;
		type_ = type;
		new_type_ = type;
		genString(origin1, origin2, null);
	}
	
	public SearchOp(ActionNode origin1, ActionNode node1, Argument arg1, String span1, 
			ActionNode origin2, ActionNode node2, Argument arg2, String span2, 
			ActionNode origin3, ActionNode node3, Argument arg3, String span3, char op, CONN_TYPE type) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;

		this.node3 = node3;
		this.arg3 = arg3;
		this.span3 = span3;
		
		op_ = op;
		type_ = type;
		genString(origin1, origin2, origin3);
	}
	
	public SearchOp(ActionNode origin1, ActionNode node1, Argument arg1, String span1, 
			ActionNode origin2, ActionNode node2, Argument arg2, String span2, 
			ActionNode origin3, ActionNode node3, Argument arg3, String span3, char op, CONN_TYPE type, CONN_TYPE type2) {
		this.node1 = node1;
		this.arg1 = arg1;
		this.span1 = span1;
		
		this.node2 = node2;
		this.arg2 = arg2;
		this.span2 = span2;

		this.node3 = node3;
		this.arg3 = arg3;
		this.span3 = span3;
		
		op_ = op;
		type_ = type;
		new_type_ = type2;
		genString(origin1, origin2, origin3);
	}
	
	private String str_ = null;
	
	public boolean isDestSwap() {
		return op_ == OP_SWAP_DESTS_;
	}
	
	public boolean isNewDest() {
		return op_ == OP_NEW_DEST_;
	}
	
	public boolean isSplitConn() {
		return op_ == OP_SPLIT_CONN_;
	}
	
	public boolean isAddDest() {
		return op_ == OP_ADD_NEW_OUTPUT_;
	}
	
	public boolean isRemoveDest() {
		return op_ == OP_REMOVE_OUTPUT_;
	}
	
	public boolean isThreeWay() {
		return op_ == OP_THREE_WAY_;
	}
	
	public boolean isChangeType() {
		return op_ == OP_CHANGE_TYPE_;
	}
	
	@Override
	public int compareTo(SearchOp arg0) {
		return str_.compareTo(arg0.str_);
	}
	public boolean equals(Object o) {
		return compareTo((SearchOp)o) == 0;
	}
	
	public int hashCode() {
		return toString().hashCode();
	}
	
	public String toString() {
		return str_;
	}
	
	public void genString(GraphInfo gi) {
		int closeness = Integer.MAX_VALUE;
		int farness = 0;
		if (isDestSwap()) {
			str_ = "OP_SWAP:";
		} else if (isAddDest()) {
			str_ = "OP_ADD:";
		} else if (isRemoveDest()) {
			str_ = "OP_REMOVE:";
		} else if (isNewDest()) {
			str_ = "OP_NEW:";
		} else if (isSplitConn()) {
			str_ = "OP_SPLIT:";
		} else if (isThreeWay()) {
			str_ = "OP_THREE:";
		} else if (isChangeType()) {
			str_ = "OP_CHANGE:";
		}
		
		if (type_ == CONN_TYPE.FOOD) {
			str_ += "FOOD:";
		} else if (type_ == CONN_TYPE.LOC) {
			str_ += "LOC:";
		} else {
			str_ += "OTHER:";
		}
		switch(op_) {
		case OP_SWAP_DESTS_:
			String s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			String s2 = span2;
			if (span2.equals("")) {
				s2 = arg2.type().toString();
			}
			ActionNode origin1 = gi.getOrigin(node1, arg1, span1);
			ActionNode origin2 = gi.getOrigin(node2, arg2, span2);
			int c = node1.index() - origin2.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			c = node2.index() - origin1.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += "(" + origin1 + ", " + origin2 + ") <-> (" + node1 + ":" + s1 + ", " + node2 + ":" + s2 + ")";
			break;
		case OP_NEW_DEST_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			s2 = span2;
			if (span2.equals("")) {
				s2 = arg2.type().toString();
			}
			ActionNode o1 = gi.getOrigin(node1, arg1, span1);
			c = node2.index() - o1.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += "(" + o1 + ") <-> (" + node1 + ":" + s1 + ", " + node2 + ":" + s2 + ")";
			break;
		case OP_ADD_NEW_OUTPUT_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			c = node1.index() - origin.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += origin + " --> + " + node1 + ":" + s1;
			break;
		case OP_REMOVE_OUTPUT_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			c = node1.index() - origin.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += origin + " -/-> + " + node1 + ":" + s1;
			break;
//		case OP_SPLIT_CONN_:
//			ActionNode or1 = gi.getOrigin(node1, arg1, span1);
//			ActionNode or2 = gi.getOrigin(node2, arg2, span2);
//			str_ += "(" + or2 + ") -> " + or1 + ":" + span1;
//			break;
		case OP_THREE_WAY_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			s2 = span2;
			if (span2.equals("")) {
				s2 = arg2.type().toString();
			}
			String s3 = span3;
			if (span3.equals("")) {
				s3 = arg3.type().toString();
			}
			ActionNode or1 = gi.getOrigin(node1, arg1, span1);
			ActionNode or2 = gi.getOrigin(node2, arg2, span2);
			ActionNode or3 = gi.getOrigin(node3, arg3, span3);
			if (or3 != null) {
				c = node1.index() - or3.index();
				if (c < closeness) {
					closeness = c;
				}
				if (c > farness) {
					farness = c;
				}
			}
			c = node3.index() - or2.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			if (or1 != null) {
				c = node2.index() - or1.index();
				if (c < closeness) {
					closeness = c;
				}
				if (c > farness) {
					farness = c;
				}
			}
			str_ += "(" + or1 + ", " + or2 + ", " + or3 + ") <-> (" + node1 + ":" + s1 + ", " + node2 + ":" + s2 +
					", " + node3 + ":" + s3 + ")";
			break;
		}
		str_ = closeness + " " + farness + " " + str_;
	}
	
	public void genString(ActionNode origin1, ActionNode origin2, ActionNode origin3) {
		int closeness = Integer.MAX_VALUE;
		int farness = 0;
		if (isDestSwap()) {
			str_ = "OP_SWAP:";
		} else if (isAddDest()) {
			str_ = "OP_ADD:";
		} else if (isRemoveDest()) {
			str_ = "OP_REMOVE:";
		} else if (isNewDest()) {
			str_ = "OP_NEW:";
		} else if (isSplitConn()) {
			str_ = "OP_SPLIT:";
		} else if (isThreeWay()) {
			str_ = "OP_THREE:";
		} else if (isChangeType()) {
			str_ = "OP_CHANGE:";
		}
		if (type_ == CONN_TYPE.FOOD) {
			str_ += "FOOD:";
		} else if (type_ == CONN_TYPE.LOC) {
			str_ += "LOC:";
		} else {
			str_ += "OTHER:";
		}
		switch(op_) {
		case OP_SWAP_DESTS_:
			String s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			String s2 = span2;
			if (span2.equals("")) {
				s2 = arg2.type().toString();
			}
			int c = node1.index() - origin1.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			c = node2.index() - origin2.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += "(" + origin1 + ", " + origin2 + ") <-> (" + node1 + ":" + s1 + ", " + node2 + ":" + s2 + ")";
			break;
		case OP_NEW_DEST_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			s2 = span2;
			if (span2.equals("")) {
				s2 = arg2.type().toString();
			}
			c = node2.index() - origin1.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += "(" + origin1 + ") <-> (" + node1 + ":" + s1 + ", " + node2 + ":" + s2 + ")";
			break;
		case OP_ADD_NEW_OUTPUT_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			c = node1.index() - origin.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += origin + " --> + " + node1 + ":" + s1;
			break;
		case OP_REMOVE_OUTPUT_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			c = node1.index() - origin.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			str_ += origin + " -/-> + " + node1 + ":" + s1;
			break;
		case OP_THREE_WAY_:
			s1 = span1;
			if (span1.equals("")) {
				s1 = arg1.type().toString();
			}
			s2 = span2;
			if (span2.equals("")) {
				s2 = arg2.type().toString();
			}
			String s3 = span3;
			if (span3.equals("")) {
				s3 = arg3.type().toString();
			}
			if (origin3 != null) {
				c = node1.index() - origin3.index();
				if (c < closeness) {
					closeness = c;
				}
				if (c > farness) {
					farness = c;
				}
			}
			c = node3.index() - origin2.index();
			if (c < closeness) {
				closeness = c;
			}
			if (c > farness) {
				farness = c;
			}
			if (origin1 != null) {
				c = node2.index() - origin1.index();
				if (c < closeness) {
					closeness = c;
				}
				if (c > farness) {
					farness = c;
				}
			}
			str_ += "(" + origin1 + ", " + origin2 + ", " + origin3 + ") <-> (" + node1 + ":" + s1 + ", " + node2 + ":" + s2 +
					", " + node3 + ":" + s3 + ")";
			break;
		}
		str_ = closeness + " " + farness + " " + str_;
	}

}
