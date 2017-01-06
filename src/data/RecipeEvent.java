package data;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import utils.Pair;
import utils.TokenCounter;
import utils.Utils;
import edu.stanford.nlp.util.StringUtils;

/**
 * This class stores information about an event that is recorded from
 * the recipe text. The event action is a verb represented by predicate_,
 * and any arguments of that predicate are stored in various data structures
 * depending on the type.
 * 
 * The direct object of the verb is stored in dobj_.
 * Prepositional arguments are stored in a TreeMap based on location in the sentence.
 * Any other arguments (e.g., adverbs) are stored in a different TreeMap.
 * 
 * @author chloe
 *
 */
public class RecipeEvent implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public static class Argument implements Comparable<Argument>, Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public enum Type {
			OBJECT("OBJECT"),
			OTHER("OTHER"),
			COOBJECT("COOBJECT"),
			DURATION("DURATION"),
			MANNER("MANNER"),
			LOCATION("LOCATION"),
			LOCOROBJ("LOCOROBJ");

			private final String name;       

			private Type(String s) {
				name = s;
			}

			public boolean equalsName(String otherName) {
				return (otherName == null) ? false:name.equals(otherName);
			}

			public String toString() {
				return name;
			}

		};

		private String str_;
		private Type type_;
		private String prep_;
		private Set<String> ingredients_;
		private List<String> ingredient_spans_;
		private List<String> non_ingredient_spans_;
		private Map<String, Set<String>> span_to_ingredient_maps_;

		public Argument(String str, Type type) {
			str_ = str;
			type_ = type;
			prep_ = null;	
			ingredients_ = new HashSet<String>();
			ingredient_spans_ = new ArrayList<String>();
			non_ingredient_spans_ = new ArrayList<String>();
			span_to_ingredient_maps_ = new HashMap<String, Set<String>>();
		}

		public Argument(String str, String prep, Type type) {
			str_ = str;
			type_ = type;
			prep_ = prep;	
			ingredients_ = new HashSet<String>();
			ingredient_spans_ = new ArrayList<String>();
			span_to_ingredient_maps_ = new HashMap<String, Set<String>>();
			non_ingredient_spans_ = new ArrayList<String>();
		}

		public String string() {
			return str_.replace(", and ", ", ").replace(" and ", ", ");
		}
		
		public void addString(String str) {
			str_ = str_ + ", " + str;
		}

		public String toString() {
			return type_ + ":" + str_.replace(", and ", ", ").replace(" and ", ", ");
		}

		public Type type() {
			return type_;
		}

		public void setType(Type type) {
			type_ = type;
		}

		public Set<String> ingredients() {
			return ingredients_;
		}

		public void addIngredient(String ingredient, String span) {
			ingredients_.add(ingredient);
			ingredient_spans_.add(span);
			Set<String> ingredients = span_to_ingredient_maps_.get(span);
			if (ingredients == null) {
				ingredients = new HashSet<String>();
				span_to_ingredient_maps_.put(span, ingredients);
			}
			ingredients.add(ingredient);
			if (type_ != Type.OBJECT) {
				type_ = Type.COOBJECT;
			}
		}
		
		public void setSpanAsIngredient(String ingredient, String span) {
			ingredients_.add(ingredient);
			ingredient_spans_.add(span);
			Set<String> ingredients = span_to_ingredient_maps_.get(span);
			if (ingredients == null) {
				ingredients = new HashSet<String>();
				span_to_ingredient_maps_.put(span, ingredients);
			}
			ingredients.add(ingredient);
			if (type_ != Type.OBJECT) {
				type_ = Type.COOBJECT;
			}
			non_ingredient_spans_.remove(span);
		}
		
		public void addNonIngredientSpan(String span) {
			non_ingredient_spans_.add(span);
		}

		public String preposition() {
			return prep_;
		}
		
		public boolean hasIngredients() {
			return ingredient_spans_.size() != 0;
		}

		public List<String> ingredientSpans() {
			return ingredient_spans_;
		}

		public Set<String> ingredientsForSpan(String span) {
			return span_to_ingredient_maps_.get(span);
		}

		public List<String> nonIngredientSpans() {
			return non_ingredient_spans_;
		}
		
		public void setNonIngredientSpans(List<String> spans) {
			non_ingredient_spans_ = spans;
		}
		
		public boolean equals(Object obj) {
			if (obj instanceof Argument) {
				return compareTo((Argument)obj) == 0;
			}
			return false;
		}

		@Override
		public int compareTo(Argument o) {
//			int cmp = toString().compareTo(o.toString());
//			if (cmp != 0) {
//				return cmp;
//			}
//			return type().compareTo(o.type());
			if (this == o) {
				return 0;
			}
			return System.identityHashCode(this) - System.identityHashCode(o);
		}
		
		public int hashCode() {
			return (toString() + " TYPE: " + type()).hashCode();
		}
	}

	private String recipe_ = "";
	private String sentence_str_ = "";
	private int sentence_index_ = -1;
	private int predicate_index_in_sentence_ = -1;
	private int event_type_id_ = -1;
	private String predicate_ = "";
	private Set<Argument> args_;
	private TreeMap<Integer, Argument> prepositional_args_by_hw_index_;
	private boolean has_imp_prep = false;
	private Argument dobj_= null;
	private TreeMap<Integer, Argument> other_args_by_hw_index_;
	private int num_implicit_args = 0;
	public List<Pair<String, String>> ordered_args = new ArrayList<Pair<String, String>>();
	
	public RecipeEvent(String predicate, String recipe, int sentence_index, int predicate_index) {
		predicate_ = predicate;
		event_type_id_ = EventType.getOrCreateEventTypeIdForPredicate(predicate);
		recipe_ = recipe;
		sentence_index_ = sentence_index;
		predicate_index_in_sentence_ = predicate_index;
		args_ = new HashSet<Argument>();

		dobj_ = null;
		prepositional_args_by_hw_index_ = new TreeMap<Integer, Argument>();
		other_args_by_hw_index_ = new TreeMap<Integer, Argument>();
		
		sentence_str_ = "";
	}
	
	public RecipeEvent(String predicate, String recipe, int sentence_index, int predicate_index,
			String sentence_str) {
		predicate_ = predicate;
		event_type_id_ = EventType.getOrCreateEventTypeIdForPredicate(predicate);
		recipe_ = recipe;
		sentence_index_ = sentence_index;
		predicate_index_in_sentence_ = predicate_index;
		args_ = new HashSet<Argument>();

		dobj_ = null;
		prepositional_args_by_hw_index_ = new TreeMap<Integer, Argument>();
		other_args_by_hw_index_ = new TreeMap<Integer, Argument>();
		
		sentence_str_ = sentence_str;
	}

	/// Methods for adding arguments to event
	public Argument setDirectObject(String dobj) {
		if (dobj_ != null) {
			dobj_.str_ += ", " + dobj;
//			System.out.println("ADDING DOBJ " + dobj_.str_);
			return dobj_;
		}
		dobj_ = new Argument(dobj, Argument.Type.OBJECT);
		if (args_.contains(dobj_)) {
			System.out.println("Used direct object. " + dobj_);
			System.exit(1);
		}
		args_.add(dobj_);
		return dobj_;
	}
	
	public String sentence_string() {
		return sentence_str_;
	}
	
	public void setSentenceString(String str) {
		sentence_str_ = str;
	}
	
	public void clear() {
		if (dobj_ != null && dobj_.string().equals("")) {
			args_.remove(dobj_);
			dobj_ = null;
		}
		Set<Integer> to_remove = new HashSet<Integer>();
		for (Integer index : prepositional_args_by_hw_index_.keySet()) {
			Argument arg = prepositional_args_by_hw_index_.get(index);
			if (arg.string().equals("")) {
				args_.remove(arg);
				to_remove.add(index);
			}
		}
		for (Integer remove : to_remove) {
			prepositional_args_by_hw_index_.remove(remove);
		}
		to_remove.clear();
		for (Integer index : other_args_by_hw_index_.keySet()) {
			Argument arg = other_args_by_hw_index_.get(index);
			if (arg.string().equals("")) {
				args_.remove(arg);
				to_remove.add(index);
			}
		}
		for (Integer remove : to_remove) {
			other_args_by_hw_index_.remove(remove);
		}
	}

	public Argument addPrepositionalArgument(String arg) {
		int index = prepositional_args_by_hw_index_.size();
		if (arg.trim().equals("")) { // implicit
			index = --num_implicit_args;
		}
		int first_space = arg.indexOf(" ");
		if (first_space == -1) {
			return addPrepositionalArgument(index, arg,
					"");
		} else {
			return addPrepositionalArgument(index, arg.substring(first_space + 1),
					arg.substring(0, first_space));
		}
	}

	public Argument addPrepositionalArgument(String arg, String prep) {
		int index = prepositional_args_by_hw_index_.size();
		if (arg.trim().equals("")) { // implicit
			index = --num_implicit_args;
		}
		return addPrepositionalArgument(index, arg, prep);
	}
	
	public Argument addPrepositionalArgument(String arg, String prep, Argument.Type type) {
		int hw_index = prepositional_args_by_hw_index_.size();
		if (arg.trim().equals("")) { // implicit
			hw_index = --num_implicit_args;
		}
		if (prepositional_args_by_hw_index_.get(hw_index) != null) {
			throw new IllegalArgumentException("Prepositional argument already documented for " + predicate_ + 
					" starting at arg# " + hw_index + ". Previous value was " + prepositional_args_by_hw_index_.get(hw_index) + ".");
		}
		Argument new_arg = new Argument(arg.toLowerCase(), prep.toLowerCase(), type);	
		
		if (args_.contains(new_arg)) {
			return null;
		}
		if (arg.equals("")) {
			if (has_imp_prep) {
				return null;
			}
			has_imp_prep = true;
		}
		args_.add(new_arg);
		prepositional_args_by_hw_index_.put(hw_index, new_arg);
		return new_arg;
	}

	public Argument addPrepositionalArgument(int hw_index, String arg) throws IllegalArgumentException {
		int first_space = arg.indexOf(" ");
		if (first_space == -1) {
			return addOtherArgument(hw_index, arg);
		} else {
			return addPrepositionalArgument(hw_index, arg.substring(first_space + 1),
					arg.substring(0, first_space));
		}
	}

	public Argument addPrepositionalArgument(int hw_index, String arg, String prep) throws IllegalArgumentException {
		if (prepositional_args_by_hw_index_.get(hw_index) != null) {
			throw new IllegalArgumentException("Prepositional argument already documented for " + predicate_ + 
					" starting at arg# " + hw_index + ". Previous value was " + prepositional_args_by_hw_index_.get(hw_index) + ".");
		}
		Argument new_arg = null;
		boolean has_noun = false;
		String[] split = arg.split(" ");
		for (String s : split) {
			if (Utils.canWordBeNounInWordNet(s) || !Utils.isSymbolOrWordInWordNet(s)) {
				has_noun = true;
				break;
			}
		}
		if (!arg.equals("") && !has_noun) {
			new_arg = new Argument(arg.toLowerCase(), prep.toLowerCase(), Argument.Type.OTHER);
		} else if (prep.matches("until|for|while") || arg.toLowerCase().contains("minute") || arg.toLowerCase().contains("hour")) {
			new_arg = new Argument(arg.toLowerCase(), prep.toLowerCase(), Argument.Type.DURATION);	
		} else if (prep.matches("in|into|over|inside|to|with|on|onto|among|from|around|throughout|outside")) {
			new_arg = new Argument(arg.toLowerCase(), prep.toLowerCase(), Argument.Type.LOCOROBJ);
		} else {
			new_arg = new Argument(arg.toLowerCase(), prep.toLowerCase(), Argument.Type.OTHER);
		}
		if (args_.contains(new_arg)) {
			return null;
		}
//		System.out.println(new_arg + " " + new_arg.type());
		if (arg.equals("")) {
			if (has_imp_prep) {
				return null;
			}
			has_imp_prep = true;
		}
		args_.add(new_arg);
		prepositional_args_by_hw_index_.put(hw_index, new_arg);
		return new_arg;
	}
	
	public boolean hasImpPrep() {
		return has_imp_prep;
	}

	public Argument addOtherArgument(String arg) {
		int index = other_args_by_hw_index_.size();
		if (arg.trim().equals("")) { // implicit
			index = --num_implicit_args;
		}
		return addOtherArgument(index, arg);
	}

	public Argument addOtherArgument(int hw_index, String arg) throws IllegalArgumentException {
		if (other_args_by_hw_index_.get(hw_index) != null) {
			throw new IllegalArgumentException("Argument already documented for " + predicate_ + " starting at arg# "
					+ hw_index + ". Previous value was " + other_args_by_hw_index_.get(hw_index) + ".");
		}
		Argument new_arg = null;
		if (arg.toLowerCase().contains("minute") || arg.toLowerCase().contains("hour")) {
			new_arg = new Argument(arg.toLowerCase(), Argument.Type.DURATION);
		} else {
			new_arg = new Argument(arg.toLowerCase(), Argument.Type.OTHER);
		}
		if (args_.contains(new_arg)) {
			return null;
		}
		args_.add(new_arg);
		other_args_by_hw_index_.put(hw_index, new_arg);
		return new_arg;
	}
	////////////

	public void addVerbToPredicate(String verb, String conj, boolean after) {
		if (conj.length() != 0) {
			conj = conj + " ";
		}
		if (after) {
			predicate_ += " " + conj + verb;
		} else {
			predicate_ = verb + " " + conj + predicate_;
		}
	}

	//////////////////
	/// Getters
	/////////////////
	public String recipeName() {
		return recipe_;
	}

	public int sentenceIndex() {
		return sentence_index_;
	}

	public int predicateIndexInSentence() {
		return predicate_index_in_sentence_;
	}

	public String predicate() {
		String[] split = predicate_.split(" ");
		if (split.length == 1) {
			return predicate_;
		}
		return split[0];
	}
	
	public String full_pred_string() {
		return predicate_;
	}

	public int eventTypeId() {
		return event_type_id_;
	}

	public Argument dobj() {
		return dobj_;
	}
	//////End getters//////

	// Counters for prepositional and other argument maps
	public int numPrepositionalArguments() {
		return prepositional_args_by_hw_index_.size();
	}

	public int numOtherArguments() {
		return other_args_by_hw_index_.size();
	}
	////////


	///////////
	///Get map iterators & access for argument strings
	///////////

	public Iterator<Argument> prepositionalArgIterator() {
		return prepositional_args_by_hw_index_.values().iterator();
	}

	public Iterator<Argument> otherArgIterator() {
		return other_args_by_hw_index_.values().iterator();
	}

	public Iterator<Integer> prepositionalIndexIterator() {
		return prepositional_args_by_hw_index_.keySet().iterator();
	}

	public Argument getPrepositionalArgAtIndex(int index) {
		return prepositional_args_by_hw_index_.get(index);
	}

	public Iterator<Integer> otherIndexIterator() {
		return other_args_by_hw_index_.keySet().iterator();
	}

	public Argument getOtherArgAtIndex(int index) {
		return other_args_by_hw_index_.get(index);
	}
	/////end getters for map iterators


	public int getNumArgumentSpans() {
		int num_arg_spans = 0;
		if (dobj_ != null) {
			num_arg_spans += dobj_.ingredient_spans_.size();
			num_arg_spans += dobj_.non_ingredient_spans_.size();
		}
		for (Argument prep_arg : prepositional_args_by_hw_index_.values()) {
			num_arg_spans += prep_arg.ingredient_spans_.size();
			num_arg_spans += prep_arg.non_ingredient_spans_.size();
		}
		for (Argument other_arg : other_args_by_hw_index_.values()) {
			num_arg_spans += other_arg.ingredient_spans_.size();
			num_arg_spans += other_arg.non_ingredient_spans_.size();
		}
		return num_arg_spans;
	}

	public String getFullPredicateString() {
		//		return predicate_ + " (S" + sentence_index_ + ",P" + predicate_index_in_sentence_ + ")";
		return predicate_;
	}

	public String toString() {

		String str = "PRED: " + predicate_ + "\n";
		if (dobj_ == null) {
			str += "   DOBJ: NULL\n";
		} else {
			str += "   DOBJ: " + dobj_.string() + "\n";
			List<String> ingredient_spans = dobj_.ingredientSpans();
			for (String span : ingredient_spans) {
				str += "      INGREDIENT SPAN: " + span + "\n";
				str += "         INGREDIENTS: " + StringUtils.join(dobj_.ingredientsForSpan(span), ", ") + "\n";
			}
			for (String span : dobj_.non_ingredient_spans_) {
				str += "      NON-INGREDIENT SPAN: " + span + "\n";
			}
		}
		Iterator<Argument> prep_it = prepositionalArgIterator();
		while (prep_it.hasNext()) {
			Argument prep_arg = prep_it.next();
			str += "   PARG: " + prep_arg.string() + "\n";
			str += "     PREP: " + prep_arg.preposition() + "\n";
			List<String> ingredient_spans = prep_arg.ingredientSpans();
			for (String span : ingredient_spans) {
				str += "      INGREDIENT SPAN: " + span + "\n";
				str += "         INGREDIENTS: " + StringUtils.join(prep_arg.ingredientsForSpan(span), ", ") + "\n";
			}
			for (String span : prep_arg.non_ingredient_spans_) {
				str += "      NON-INGREDIENT SPAN: " + span + "\n";
			}
		}
		Iterator<Argument> other_it = otherArgIterator();
		while (other_it.hasNext()) {
			Argument other_arg = other_it.next();
			str += "   OARG: " + other_arg.string() + "\n";
			List<String> ingredient_spans = other_arg.ingredientSpans();
			for (String span : ingredient_spans) {
				str += "      INGREDIENT SPAN: " + span + "\n";
				str += "         INGREDIENTS: " + StringUtils.join(other_arg.ingredientsForSpan(span), ", ") + "\n";
			}
			for (String span : other_arg.non_ingredient_spans_) {
				str += "      NON-INGREDIENT SPAN: " + span + "\n";
			}
		}
		return str;
	}
}
