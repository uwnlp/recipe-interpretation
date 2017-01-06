package utils;

import java.util.HashSet;
import java.util.Set;

import edu.stanford.nlp.util.StringUtils;

public class Measurements {

	// Set of common recipe measurement units
	private static Set<String> amounts = new HashSet<String>();
	static {
		amounts.add("cup");
		amounts.add("cups");
		amounts.add("c");
		amounts.add("c.");
		
		amounts.add("teaspoon");
		amounts.add("teaspoons");
		amounts.add("tsp");
		amounts.add("tsps");
		amounts.add("tsp.");
		amounts.add("tsps.");
		amounts.add("t.");
		
		amounts.add("tablespoon");
		amounts.add("tablespoons");
		amounts.add("tbsp");
		amounts.add("tbsps");
		amounts.add("tbsp.");
		amounts.add("tbsps.");
		amounts.add("T.");
		amounts.add("tb.");
		amounts.add("tb");
		amounts.add("tbl");
		amounts.add("tbls");
		amounts.add("tbl.");
		amounts.add("tbls.");
		
		amounts.add("quart");
		amounts.add("quarts");
		amounts.add("qt");
		amounts.add("qts");
		amounts.add("qt.");
		amounts.add("qts.");
		
		amounts.add("liter");
		amounts.add("liters");
		amounts.add("litre");
		amounts.add("litres");
		amounts.add("lt");
		amounts.add("lts");
		amounts.add("lt.");
		amounts.add("lts.");
		
		amounts.add("gallon");
		amounts.add("gallons");
		amounts.add("gal");
		amounts.add("gal.");
		amounts.add("gals");
		amounts.add("gals.");
		
		amounts.add("scoop");
		amounts.add("scoops");
		amounts.add("pinch");
		amounts.add("sprinkle");
		amounts.add("dash");
		amounts.add("pinch");
		amounts.add("can");
		amounts.add("cans");
		
		amounts.add("ml");
		amounts.add("ml.");
		amounts.add("mls");
		amounts.add("milliliter");
		amounts.add("milliliters");
		amounts.add("millilitre");
		amounts.add("millilitres");
		
		amounts.add("ounce");
		amounts.add("ounces");
		amounts.add("oz");
		amounts.add("ozs");
		amounts.add("pound");
		amounts.add("pounds");
		amounts.add("lb");
		amounts.add("lbs");
		amounts.add("lb.");
		amounts.add("lbs.");
		
		amounts.add("piece");
		amounts.add("pieces");
		
		amounts.add("remaining");
		amounts.add("rest");
		amounts.add("approximately");
		amounts.add("about");
	}
	
	private static Set<String> times = new HashSet<String>();
	static {
		times.add("min.");
		times.add("sec.");
		times.add("hr.");
		times.add("hrs.");
		times.add("secs.");
		times.add("mins.");
		times.add("m.");
		times.add("s.");
		times.add("minute");
		times.add("minutes");
		times.add("second");
		times.add("seconds");
		times.add("hour");
		times.add("hours");
		times.add("deg");
		times.add("degs");
	}
	
	public static boolean isAmount(String amt) {
		return amounts.contains(amt.toLowerCase());
	}
	
	public static boolean isTime(String time) {
		return times.contains(time.toLowerCase());
	}
	/**
	 * Checks a string to see if it contains a number or word form of a number.
	 * Note: does not check for every single written number imaginable. Strings with
	 * negative numbers return false, which should be fine since recipes shouldn't
	 * contain negative numbers. 
	 * 
	 * TODO(chloe): get every number, if necessary
	 * 
	 * Examples of strings that are numbers:
	 * 2
	 * 1.5
	 * one
	 * ten
	 * hundred
	 * 
	 * @param str input string
	 * @return Returns true if the string contains a number
	 */
	public static boolean isNumericString(String str) {
		if (str.matches("one|two|three|four|five|six|seven|eight|nine|ten|dozen|twenty|thirty|forty|fifty|hundred")) {
			return true;
		}
		return str.matches("\\d+([\\./-]\\d+)?");
	}
	
	public static boolean isNumericStringLong(String str) {
		if (str.matches("one|two|three|four|five|six|seven|eight|nine|ten|dozen|twenty|thirty|forty|fifty|hundred")) {
			return true;
		}
		return str.matches("\\d+([\\./-]\\d+)?( \\d+([\\./-]\\d+)?)*");
	}
	
	public static boolean isMealMasterMeasure(String str) {
		return str.trim().equals("") || str.matches("x|ml|cl|dl|l|mg|cg|dg|g|kg|fl|pt|qt|ga|oz|lb|dr|ds|pn|ts|tb|c|sm|md|lg|cn|pk|ct|sl|bn|ea|cb|t|T|TB");
	}
	
	/**
	 * Splits an input string into a pair of strings, one with any amount string (i.e., numeric string and/or
	 * measurement string)  that appears in the input and the rest of the string in the latter string of the pair.
	 * 
	 * @param str input string
	 * @return pair of strings (amount, rest)
	 */
	public static Pair<String, String> splitAmountString(String str) {
		String amount = "";
		String ingredient = "";
		boolean in_amount = true;
		String[] split = str.split(" ");
		for (String s : split) {
			if (s.trim().equals("")) {
				continue;
			}
			if (s.trim().matches("the|a|an")) {
				continue;
			}
//			System.out.println(s + " " + in_amount + " " + isNumericString(s) + " " + amounts.contains(s));
//			if (s.length() > 2) {
//				System.out.println(Character.getNumericValue(s.charAt(1)) + " " + Character.isSpaceChar(s.charAt(1)));
//				System.out.println(Character.getType(s.charAt(1)) + " " + Character.isDefined(s.charAt(1)) + "|" + s.charAt(1) + "|");
//				System.out.println(s.charAt(1) == ' ');
//			}
			String cleaned = s.replaceAll("[^A-Za-z0-9]","");
			if (in_amount && isNumericString(s)) {
				if (amount.length() == 0) {
					amount = s;
				} else {
					amount += " " + s;
				}
			} else if (in_amount && amounts.contains(cleaned)) {
				if (amount.length() == 0) {
					amount = s;
				} else {
					amount += " " + s;
				}
			} else {
				if (ingredient.length() == 0) {
					ingredient = s;
				} else {
					ingredient += " " + s;
				}
				in_amount = false;
			}
		}
		if (ingredient.length() == 0) {
			return new Pair<String, String>("", str);
		}
		return new Pair<String, String>(amount.toLowerCase(), ingredient.toLowerCase().replaceAll("[^A-Za-z0-9 /]",""));
	}
	
	public static void main(String args[]) {
		System.out.println(Measurements.isNumericString("1-2"));
		System.out.println(Measurements.isNumericString("1/2"));
		System.out.println(Measurements.isNumericStringLong("1 1/2"));
		System.out.println(Measurements.isNumericString("dozen"));
		System.out.println(Measurements.isNumericString("1.2"));
		System.out.println(Measurements.isNumericString("1"));
		System.out.println(Measurements.isNumericString("1/help"));
		System.out.println(Measurements.isNumericString("d"));
		System.out.println(Measurements.isNumericString("pounds"));
		System.out.println(splitAmountString("one pound potatoes"));
		System.out.println(splitAmountString("1 1/2 cups sugar"));
		System.out.println(splitAmountString("1 1/2 cups"));
		System.out.println(splitAmountString("muffin cups"));
		
		String[] split = "apples, pears, and banandas".split(",| and ");
		for (String s : split) {
			System.out.println(s);
		}
		System.out.println(StringUtils.join(split, ", "));
		
		split = "INGREDIENT: bananas".split(": ");
		for (String s : split) {
			System.out.println(s);
		}
	}
}
