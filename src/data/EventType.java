package data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import edu.stanford.nlp.util.StringUtils;

/**
 * Data structure for the type of a RecipeEvent.
 * 
 * Each EventType knows the predicates that represent it.
 * Currently, each predicate may only belong to one EventType (i.e., 
 * we do not allow multiple senses).
 * 
 * @author chloe
 *
 */
public class EventType {
	// List of all EventTypes. An EventType's id is its index in the list.
	private static List<EventType> event_types_ = new ArrayList<EventType>();
	/**
	 * Retrieve an EventType based on its id
	 * @param event_type_id id of the EventType being searched for
	 * @return the EventType object associated with the input id
	 */
	public static EventType getEventType(int event_type_id) {
		return event_types_.get(event_type_id);
	}
	
	// Map from string predicates to the id of the EventType they represent
	private static Map<String, Integer> predicate_to_event_type_id = new HashMap<String, Integer>();
	/**
	 * Retrieve the id of the EventType that a predicate represents
	 * @param predicate input String predicate
	 * @return the Integer id of the EventType of that predicate, null if predicate has no id
	 */
	public static Integer getEventTypeIdForPredicate(String predicate) {
		return predicate_to_event_type_id.get(predicate);
	}
	
	/**
	 * Retrieve the id of the EventType that a predicate represents. If none
	 * exists, create a new EventType and return the id of that EventType.
	 * 
	 * @param predicate String
	 * @return Integer id of the found (or created) EventType
	 */
	public static Integer getOrCreateEventTypeIdForPredicate(String predicate) {
		Integer id = getEventTypeIdForPredicate(predicate);
		if (id != null) {
			return id;
		}
		// Must create new EventType
		EventType event_type = new EventType();
		id = event_types_.size();
		event_types_.add(event_type);
		
		// Add predicate to that EventType's predicates_ set.
		event_type.predicates_.add(predicate);
		
		// Add mapping from predicate to id to the static map.
		predicate_to_event_type_id.put(predicate, id);
		
		return id;
	}
	
	// Special Start type. For use in probability distributions when
	// a RecipeEvent is the first event seen in a recipe.
	private static String START_STRING = "_START_";
	private static int start_type_id;
	static {
		start_type_id = getOrCreateEventTypeIdForPredicate(START_STRING);
	}
	public static int getStartTypeId() {
		return start_type_id;
	}
	
	// hidden constructor. All construction should be done through the
	// getOrCreateEventTypeIdForPredicate() method.
	private EventType() {
	}
	
	// Ordered set of predicates for an EventType
	private Set<String> predicates_ = new TreeSet<String>();
	
	public Iterator<String> predicateIterator() {
		return predicates_.iterator();
	}
	
	public String getAllPredicatesString() {
		return StringUtils.join(predicates_, ", ");
	}
}
