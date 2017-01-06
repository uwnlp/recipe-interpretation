package data;

import java.util.ArrayList;
import java.util.List;

public class ProjectParameters {
	public static String DEFAULT_DATA_DIRECTORY = "AllRecipes_20_Args/";
	public static String ACTIONGRAPH_SUFFIX = "-ad/";
	public static String TOKEN_FILE = "tokens.txt";
	public static String NOUN_FILE = "nouns.txt";
	public static String ANNOTATION_SUFFIX = "-ann";
	public static String STEP_SUFFIX = "-steps";
	public static String ARG_SUFFIX = "-args";
	public static String DOT_SUFFIX = "-graph";
	public static String GOLDDOT_SUFFIX = "-goldgraph";
	public static String FULLTEXT_SUFFIX = "-fulltext";
	public static String MODEL_SUFFIX = "-model";
	public static String CHUNKED_SUFFIX = "-chunked";
	public static boolean VERBOSE = false;
	public static boolean INCORPORATE_WHEN_CONNECTING = true;
	public static boolean ALWAYS_INCORPORATE_IMP_OBJ = false;
	public static boolean USE_LEXICALIZED_FEATURES = false;
	public static int TOKEN_NUM = 1;
	public static String[] ALL_RECIPE_TYPES = {
		"BananaMuffins", "PulledPork", "PumpkinPie", "VeggiePizza", 
		"BeefMeatLoaf", "ChickenSalad", "BeefChilli", "BeefStroganoff", "CarrotCake", "MacAndCheese", 
		"DeviledEggs", "EggNoodles", 
		"PotatoSalad", "CheeseBurger", "Coleslaw", "CornChowder",  "ChickenStirFry", "PecanPie", 
		"MeatLasagna", "FrenchToast"
		};
	
	// eval params
	public static boolean exact_match = true;
	public static boolean ignore_other_args = true;
	public static boolean account_for_inter_sentence_edges = true;
	
	public static List<String> rel_order = new ArrayList<String>();
	static {
		rel_order.add("object");
		rel_order.add("co-object");
		rel_order.add("location");
		rel_order.add("duration");
		rel_order.add("other");
	}

	public static final String EVENT_NODE_DOT_SPECIFICATION = "shape=oval, style=filled, fillcolor=azure";
	public static final String END_NODE_DOT_SPECIFICATION = "shape=oval, style=filled, fillcolor=plum";
	public static final String INGREDIENT_DOT_SPECIFICATION = "shape=box, style=filled, fillcolor=peachpuff";
	public static final String OTHER_INPUT_DOT_SPECIFICATION = "shape=box, style=filled, fillcolor=white";
}
