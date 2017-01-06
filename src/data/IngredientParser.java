package data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import preprocessing.Lemmatizer;
import utils.Measurements;
import utils.Pair;
import utils.TokenCounter;
import utils.Utils;

/**
 * Parser that reads an ingredient list out of an AllRecipes recipe.
 * This class also identifies ingredients within string spans of the
 * arguments of a RecipeEvent.
 * 
 * @author chloe
 *
 */
public class IngredientParser {

	private static Lemmatizer lemmatizer_ = new Lemmatizer();

	/**
	 * Reads in the ingredient list from a file and extracts the ingredients
	 * without measurement amounts.
	 * 
	 * Uses the Measurements class to parse the amount from the ingredient text.
	 * 
	 * This function assumes the file has a line that says Ingredients
	 * and that each non-empty line afterwards has one ingredient on it. If a line
	 * starts with Data Parsed, it is the end of the file.
	 * 
	 * 
	 * @param fulltext File with the full text
	 * @return
	 * @throws IOException
	 */
	public static Set<String> parseIngredientsFromFullTextFile(File fulltext) throws IOException, IllegalArgumentException {
		Set<String> ingredients = new HashSet<String>();
		BufferedReader br = new BufferedReader(new FileReader(fulltext));
		String line;
		boolean in_ingredients = false;
		while ((line = br.readLine()) != null) {
			if (line.trim().length() == 0) {
				continue;
			} else if (line.trim().equals("Ingredients")) {
				in_ingredients = true;
			} else if (line.trim().startsWith("Data Parsed")) {
				break;
			} else if (in_ingredients) {
				if (line.trim().endsWith(":")) {
					continue; // Could be a subpart heading that should be ignored for now.
				}
				String ingredient_line = line.trim();
				line = br.readLine();
				while (line.trim().length() != 0) {
					ingredient_line += " " + line.trim();
					line = br.readLine();
				}
				String[] split = ingredient_line.split(" and ");
				for (String ing : split) {
					if (ing.trim().equals("")) {
						continue;
					}
					ing = ing.replace(",", "").replace("(", "").replace(")", "").replace(":", "").replace("  ", " ");
					Pair<String, String> amount_and_ingredient = Measurements.splitAmountString(ing);
					Pair<List<String>, List<String>> tagsAndTokens = lemmatizer_.tagAndTokenize(amount_and_ingredient.getSecond());
					//				System.out.println(tagsAndTokens);
					String new_ingredient_str = "";
					List<String> tags = tagsAndTokens.getFirst();
					List<String> tokens = tagsAndTokens.getSecond();
					for (int t = 0; t < tokens.size(); t++) {
						if (!tags.get(t).equals("IN") && !tags.get(t).equals("DT") && !tags.get(t).startsWith("PRP") && !tags.get(t).equals("VBD")) {
							if (Measurements.isNumericString(tokens.get(t))) {
								continue;
							}
							new_ingredient_str += tokens.get(t).replaceAll("[^A-Za-z0-9/]","").toLowerCase() + " ";
						}
					}

					ingredients.add(new_ingredient_str.trim());
				}
			}
		}
		br.close();
		if (!in_ingredients) {
			throw new IllegalArgumentException("File " + fulltext.getName() + 
					" is not in the appropriate structure to parse an ingredient list from.");
		}
		return ingredients;
	}
	
	public static Set<String> parseIngredientsFromFullTextFileNoTagger(File fulltext) throws IOException, IllegalArgumentException {
		Set<String> ingredients = new HashSet<String>();
		BufferedReader br = new BufferedReader(new FileReader(fulltext));
		String line;
		boolean in_ingredients = false;
		while ((line = br.readLine()) != null) {
			if (line.trim().length() == 0) {
				continue;
			} else if (line.trim().equals("Ingredients")) {
				in_ingredients = true;
			} else if (line.trim().startsWith("Data Parsed")) {
				break;
			} else if (in_ingredients) {
				if (line.trim().endsWith(":")) {
					continue; // Could be a subpart heading that should be ignored for now.
				}
				String ingredient_line = line.trim();
				line = br.readLine();
				while (line.trim().length() != 0) {
					ingredient_line += " " + line.trim();
					line = br.readLine();
				}
				String[] split = ingredient_line.split(" and ");
				for (String ing : split) {
					if (ing.trim().equals("")) {
						continue;
					}
					Pair<String, String> amount_and_ingredient = Measurements.splitAmountString(ing);
					String[] tokens = amount_and_ingredient.getSecond().split(" ");
					String new_ingredient_str = "";
					for (int t = 0; t < tokens.length; t++) {
						String token = tokens[t];
						token = token.replaceAll("[^A-Za-z0-9/]","");
						if (!Utils.canWordBeNounOrAdjOrVerbInWordNet(token)) {
							continue;
						}
						if (Measurements.isNumericString(token)) {
							continue;
						}
						new_ingredient_str += token + " ";
					}

					ingredients.add(new_ingredient_str.trim());
				}
			}
		}
		br.close();
		if (!in_ingredients) {
			throw new IllegalArgumentException("File " + fulltext.getName() + 
					" is not in the appropriate structure to parse an ingredient list from.");
		}
		return ingredients;
	}


	public static String checkStringForIngredient(String arg, Set<String> ingredients) {
		int best_count = 0;
		String best_ingredient = null;	
		Iterator<String> ingredient_it = ingredients.iterator();
		while (ingredient_it.hasNext()) {
			String ingredient = ingredient_it.next();

			int overlap = Utils.wordOverlapCount(arg, ingredient);
			if (overlap > best_count) {
				best_count = overlap;
				best_ingredient = ingredient;
			}
		}
		return best_ingredient;
	}

	public static Pair<String, Double> checkStringForIngredientUseTokenizer(String amt, 
			String arg, Set<String> ingredients, Set<String> used_ingredients, boolean allow_partials) {
		double best_count = 0;
		String best_ingredient = null;

		Iterator<String> ingredient_it = ingredients.iterator();
		while (ingredient_it.hasNext()) {
			String ingredient = ingredient_it.next();
			if (amt.equals("") && used_ingredients.contains(ingredient)) {
				continue;
			}
			if (Measurements.isNumericString(amt) && used_ingredients.contains(ingredient)) {
				continue;
			}
			//getWordOverlapUseTokenizer
			Pair<List<String>, List<String>> overlaps = Utils.getWordOverlapUseWordNet(arg, ingredient);
			List<String> overlap = overlaps.getFirst();
			List<String> partial_overlap = overlaps.getSecond();
			if (overlap.size() != 0) {
				boolean has_noun_overlap = false;
				for (String s : overlap) {
//					if (TokenCounter.nouns_.contains(s)) {
					if (Utils.isMostlyNounInWordNet(s)) {
						has_noun_overlap = true;
					}
				}
				if (!has_noun_overlap) {
					continue;
				}
				if (overlap.size() > best_count) {
					String[] split = arg.split(" ");
					int num = 0;
					for (String s : split) {
						if (s.trim().length() < 3) {
							continue;
						}
						num++;
					}
					if (!used_ingredients.contains(ingredient) || overlap.size() == num) {
						best_count = overlap.size();
						best_ingredient = ingredient;
					}
				}
			} else if (allow_partials && partial_overlap.size() != 0) {
				double cnt = partial_overlap.size() * 0.25;
				if (cnt > best_count) {
					if (!used_ingredients.contains(ingredient)) {
						best_count = cnt;
						best_ingredient = ingredient;
					}
				}
			}
		}
		return new Pair<String, Double>(best_ingredient, best_count);
	}

	public static Double checkStringForIngredientUseTokenizer(String amt, String arg, String ingredient) {
		double best_count = 0;

		//getWordOverlapUseTokenizer
		Pair<List<String>, List<String>> overlaps = Utils.getWordOverlapUseTokenizer(arg, ingredient);
		List<String> overlap = overlaps.getFirst();
		List<String> partial_overlap = overlaps.getSecond();
		if (overlap.size() != 0) {
			best_count = overlap.size();
		} else if (partial_overlap.size() != 0) {
			double cnt = partial_overlap.size() * 0.25;
			if (cnt > best_count) {
				best_count = cnt;
			}
		}
		return best_count;
	}

	public static Map<String, Set<String>> checkArgForIngredients(String arg, Set<String> ingredients) {
		// first check to see if it can be split based on grammatical enumeration clues
		String[] split = arg.split(",| and ");
		Set<String> split_list = new HashSet<String>();
		for (String s : split) {
			if (s.trim().length() != 0) {
				split_list.add(s);
			}
		}

		Set<String> used_ingredients = new HashSet<String>();
		Map<String, Set<String>> found_ingredients = new HashMap<String, Set<String>>();
		for (String span : split) {
			boolean found_ingredient = true;
			while (found_ingredient) {
				found_ingredient = false;
				int best_count = 0;
				String best_ingredient = null;	
				Iterator<String> ingredient_it = ingredients.iterator();
				while (ingredient_it.hasNext()) {
					String ingredient = ingredient_it.next();
					if (used_ingredients.contains(ingredient)) {
						continue;
					}
					int overlap = Utils.wordOverlapCount(span, ingredient);
					if (overlap > best_count) {
						best_count = overlap;
						best_ingredient = ingredient;
						found_ingredient = true;
					}
				}
				if (best_ingredient != null) {
					Set<String> ingred = found_ingredients.get(span);
					if (ingred == null) {
						ingred = new HashSet<String>();
						found_ingredients.put(span, ingred);
					}
					ingred.add(best_ingredient);
					used_ingredients.add(best_ingredient);
					String[] ingredient_tokens = best_ingredient.split(" ");
					for (String token : ingredient_tokens) {
						if (span.contains(token)) {
							span = span.replaceFirst(token, "");
							span = span.replace("  ", " ");
						}
					}
				}
			}
		}

		for (String span : split_list) {
			if (!found_ingredients.containsKey(span)) {
				found_ingredients.put(span, null);
			}
		}
		return found_ingredients;
	}
	
	public static String checkSpanForIngredients(String span, Set<String> ingredients) {
		// first check to see if it can be split based on grammatical enumeration clues

		Set<String> used_ingredients = new HashSet<String>();
		Map<String, Set<String>> found_ingredients = new HashMap<String, Set<String>>();
			boolean found_ingredient = true;
			while (found_ingredient) {
				found_ingredient = false;
				int best_count = 0;
				int bad_count = 0;
				String best_ingredient = null;	
				Iterator<String> ingredient_it = ingredients.iterator();
				while (ingredient_it.hasNext()) {
					String ingredient = ingredient_it.next();
					if (used_ingredients.contains(ingredient)) {
						continue;
					}
					int overlap = Utils.wordOverlapCount(span, ingredient);
					int bad = ingredient.split(" ").length - overlap;
					if (overlap > best_count || (overlap == best_count && bad < bad_count)) {
						best_count = overlap;
						best_ingredient = ingredient;
						found_ingredient = true;
					}
				}
				if (best_count != 0) {
					return best_ingredient;
//					Set<String> ingred = found_ingredients.get(span);
//					if (ingred == null) {
//						ingred = new HashSet<String>();
//						found_ingredients.put(span, ingred);
//					}
//					ingred.add(best_ingredient);
//					used_ingredients.add(best_ingredient);
//					String[] ingredient_tokens = best_ingredient.split(" ");
//					for (String token : ingredient_tokens) {
//						if (span.contains(token)) {
//							span = span.replaceFirst(token, "");
//							span = span.replace("  ", " ");
//						}
//					}
				}
			}
			return null;
//		return found_ingredients;
	}

	/**
	 * Updates a Collection of RecipeEvents by marking Arguments of the RecipeEvents with
	 * the first appearances from a set of ingredients.
	 * 
	 * @param ingredient_set
	 * @param event_it
	 * @return
	 */
	public static Set<String> addIngredientsToEventList(Set<String> ingredient_set, Iterator<RecipeEvent> event_it) {
		Set<String> ingredients = new HashSet<String>(ingredient_set);

		// Check if arguments contain ingredients in the set.
		while (event_it.hasNext()) {
			RecipeEvent event = event_it.next();
			//			System.out.println("ADDING " + event.predicate());
			//			System.out.println(event.toString());
			//			System.out.println(ingredients);
			RecipeEvent.Argument dobj = event.dobj();
			if (dobj != null) {
				if (!dobj.string().equals("")) {
					Map<String, Set<String>> found_ingredients_map = checkArgForIngredients(dobj.string(), ingredients);
					if (found_ingredients_map.size() != 0) {
						for (String span : found_ingredients_map.keySet()) {
							Set<String> found_ingredients = found_ingredients_map.get(span);
							if (found_ingredients == null) {
								dobj.addNonIngredientSpan(span);
							} else {
								for (String found_ingredient : found_ingredients) {
									// Remove the ingredient from the set since it has now been seen.
									// REMOVE ONLY if no measurement is given in text
									Pair<String, String> measurement_split = Measurements.splitAmountString(span);
									if (measurement_split.getFirst().equals("")) {
										ingredients.remove(found_ingredient);
									}
									// Add the ingredient to the argument with the specified string span.
									dobj.addIngredient(found_ingredient, span);
								}
							}
						}
					} else {
						dobj.addNonIngredientSpan(dobj.string());
					}
					//					System.out.println(dobj + " " + dobj.nonIngredientSpans());
				}
			}

			Iterator<RecipeEvent.Argument> prep_arg_it = event.prepositionalArgIterator();
			while (prep_arg_it.hasNext()) {
				RecipeEvent.Argument prep_arg = prep_arg_it.next();
				if (prep_arg.string().equals("")) {
					continue;
				}
				//				System.out.println(prep_arg);
				Map<String, Set<String>> found_ingredients_map = checkArgForIngredients(prep_arg.string(), ingredients);
				if (found_ingredients_map.size() != 0) {
					for (String span : found_ingredients_map.keySet()) {
						Set<String> found_ingredients = found_ingredients_map.get(span);
						if (found_ingredients == null) {
							prep_arg.addNonIngredientSpan(span);
						} else {
							for (String found_ingredient : found_ingredients) {
								// Remove the ingredient from the set since it has now been seen.
								// REMOVE ONLY if no measurement is given in text
								Pair<String, String> measurement_split = Measurements.splitAmountString(span);
								if (measurement_split.getFirst().equals("")) {
									ingredients.remove(found_ingredient);
								}
								// Add the ingredient to the argument with the specified string span.
								prep_arg.addIngredient(found_ingredient, span);
							}

						}
					}
				} else {
					prep_arg.addNonIngredientSpan(prep_arg.string());
				}
				//				System.out.println(prep_arg + " " + prep_arg.nonIngredientSpans());
			}

			// Note: it is unlikely that ingredients are in other kinds of arguments, but we'll check
			// 	anyway.
			Iterator<RecipeEvent.Argument> other_arg_it = event.otherArgIterator();
			while (other_arg_it.hasNext()) {
				RecipeEvent.Argument other_arg = other_arg_it.next();
				if (other_arg.string().equals("")) {
					continue;
				}
				//				System.out.println("other " + other_arg);
				Map<String, Set<String>> found_ingredients_map = checkArgForIngredients(other_arg.string(), ingredients);
				if (found_ingredients_map.size() != 0) {
					for (String span : found_ingredients_map.keySet()) {
						Set<String> found_ingredients = found_ingredients_map.get(span);
						if (found_ingredients == null) {
							other_arg.addNonIngredientSpan(span);
						} else {
							for (String found_ingredient : found_ingredients) {
								// Remove the ingredient from the set since it has now been seen.
								ingredients.remove(found_ingredient);
								// Add the ingredient to the argument with the specified string span.
								other_arg.addIngredient(found_ingredient, span);
							}

						}
					}
				} else {
					other_arg.addNonIngredientSpan(other_arg.string());
				}
			}
			//			System.out.println("ADDED " + event);
		}
		return ingredients;
	}

}
