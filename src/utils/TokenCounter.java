package utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import data.ProjectParameters;
import preprocessing.Lemmatizer;

public class TokenCounter {

	public static Lemmatizer lemmatizer_ = new Lemmatizer();
	public static Map<String, Integer> token_counts_ = null; //new HashMap<String, Integer>();
	public static Set<String> nouns_ = null; //new HashSet<String>();

	static {
		try {
		BufferedReader token_br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "tokens.txt"));
		String line = null;
		while ((line = token_br.readLine()) != null) {
			TokenCounter.token_counts_.put(line, 1);
		}
		token_br.close();

		BufferedReader noun_br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "nouns.txt"));
		line = null;
		while ((line = noun_br.readLine()) != null) {
			TokenCounter.nouns_.add(line);
		}
		} catch (Exception ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}
	
	// nouns, adjectives, verbs
	private static boolean nva_only_ = true;
	
	public static Pair<List<String>, List<String>> addTokens(String str) {
		str = str.trim();
		Pair<List<String>, List<String>> tagsAndTokens = lemmatizer_.tagAndTokenize(str);
		List<String> tags = tagsAndTokens.getFirst();
		List<String> tokens = tagsAndTokens.getSecond();
//		System.out.println(tagsAndTokens);
		
		for (int t = 0; t < tokens.size(); t++) {
			String token = tokens.get(t);
			if (token.equals("")) {
				continue;
			}
			token = token.toLowerCase();
			if (Measurements.isNumericString(token)) {
				continue;
			}
			if (token.length() < 3) {
				continue;
			}
			if (token.matches("be|is|was|were")) {
				continue;
			}
			String tag = tags.get(t);
			if (nva_only_ && !(tag.startsWith("N") || tag.startsWith("J") || tag.startsWith("V"))) {
				continue;
			}
			token = token.replaceAll("[^A-Za-z0-9]","");
//			System.out.println(token);
			Integer cnt = token_counts_.get(token);
			if (cnt == null) {
				token_counts_.put(token, 1);
			} else {
				token_counts_.put(token, cnt.intValue() + 1);
			}
			if (tag.startsWith("N")) {
				nouns_.add(token);
			}
		}
		return tagsAndTokens;
	}

	public static Set<String> extractImportantTokensUseKnownTokens(String str) {
		Set<String> important_tokens = new HashSet<String>();
		String[] tokens = str.split(" ");
		for (String token : tokens) {
			if (token_counts_.containsKey(token)) {
				important_tokens.add(token);
			}
		}
		return important_tokens;
	}
	
	public static Set<String> extractImportantTokens(String str) {
		Set<String> important_tokens = new HashSet<String>();
		str = str.trim();
		Pair<List<String>, List<String>> tagsAndTokens = lemmatizer_.tagAndTokenize(str);
		List<String> tags = tagsAndTokens.getFirst();
		List<String> tokens = tagsAndTokens.getSecond();
		
		for (int t = 0; t < tokens.size(); t++) {
			String token = tokens.get(t);
			if (token.equals("")) {
				continue;
			}
			token = token.toLowerCase();
			if (Measurements.isNumericString(token)) {
				continue;
			}
			if (token.matches("be|is|was|were")) {
				continue;
			}
			if (nva_only_ && !(tags.get(t).startsWith("N") || tags.get(t).startsWith("J") || tags.get(t).startsWith("V"))) {
				continue;
			}
			if (tags.get(t).startsWith("N")) {
				nouns_.add(token);
			}
			important_tokens.add(token);
		}
		return important_tokens;
	}
	
	public static boolean tokenCountGreaterThanOrEqualTo(String token, int min) {
		token = token.toLowerCase();
		token = token.replaceAll("[^A-Za-z0-9]","");
		if (Measurements.isNumericString(token)) {
			return false;
		}
		if (min == 0) {
			return true;
		}
		Integer cnt = token_counts_.get(token);
		if (cnt == null) {
			return false;
		}
		return cnt.intValue() >= min;
	}
}
