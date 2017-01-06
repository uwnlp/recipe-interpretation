package utils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import preprocessing.Lemmatizer;
import data.ProjectParameters;
import edu.smu.tspell.wordnet.Synset;
import edu.smu.tspell.wordnet.SynsetType;
import edu.smu.tspell.wordnet.WordNetDatabase;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.util.StringUtils;

public class Utils {

	public static String ignoredRegexOld = "-rrb-|-lrb-|the|a|f|c|an|to|for|is|was|ground|mashed|well|done|cooked|"
			+ "cooled|chopp ed|sliced|boiled|large|small|big|medium|and|or|in|at|taste|minute|minutes|hour|hours|with|by|from|on|up|no|yes|cup|cups";
	//	public static String ignoredRegex = "";

	public static Lemmatizer lemmatizer_ = new Lemmatizer();
	static {
		System.setProperty("wordnet.database.dir", "/usr/local/WordNet-3.0/dict");
	}
	private static WordNetDatabase database = WordNetDatabase.getFileInstance();

	/**
	 * Returns the number of words that overlap between two strings.
	 * The words are found by splitting the input strings on spaces.
	 * Certain basic words are ignored (e.g., "the", "a").
	 * 
	 * The function also does a hackish test to see if the plural or
	 * singular forms of words match (by adding or removing an 's').
	 * These partial matches also count fully towards the overlap count.
	 * 
	 * @param str1 input string
	 * @param str2 input string
	 * @return number of words that overlap
	 */
	public static int wordOverlapCount(String str1, String str2) {
		str1 = str1.toLowerCase();
		Pair<String, String> remove_amounts = Measurements.splitAmountString(str1);
		str1 = remove_amounts.getSecond();
		Pair<List<String>, List<String>> tagsAndTokens = lemmatizer_.tagAndTokenize(str1);
		String new_ingredient_str = "";
		List<String> tags = tagsAndTokens.getFirst();
		List<String> tokens = tagsAndTokens.getSecond();
		for (int t = 0; t < tokens.size(); t++) {
			if ((tags.get(t).startsWith("N") || tags.get(t).startsWith("V") || tags.get(t).startsWith("J"))) {
				new_ingredient_str += tokens.get(t) + " ";
			}
		}
		str1 = new_ingredient_str;

		str2 = str2.toLowerCase();
		remove_amounts = Measurements.splitAmountString(str2);
		str2 = remove_amounts.getSecond();
		tagsAndTokens = lemmatizer_.tagAndTokenize(str2);
		new_ingredient_str = "";
		tags = tagsAndTokens.getFirst();
		tokens = tagsAndTokens.getSecond();
		for (int t = 0; t < tokens.size(); t++) {
			if ((tags.get(t).startsWith("N") || tags.get(t).startsWith("V") || tags.get(t).startsWith("J"))) {
				new_ingredient_str += tokens.get(t) + " ";
			}
		}
		str2 = new_ingredient_str;

		String[] split1 = str1.split(" ");
		String[] split2 = str2.split(" ");
		HashSet<String> words1 = new HashSet<String>();
		for (String word1 : split1) {
			// ignore certain words for now
			// TODO(chloe): make this more robust
			word1 = word1.toLowerCase();
			words1.add(word1);
		}
		int num_overlap = 0;
		for (String word2 : split2) {
			word2 = word2.toLowerCase();
			for (String word1 : words1) {
				if (word1.equals(word2)) {
					num_overlap++;
				} else if (word2.equals(word1)) {
					num_overlap++;
				} else if (word1.endsWith("s") && word2.equals(word1.substring(0, word1.length() - 1))) {
					num_overlap++;
				} else if (word2.endsWith("s") && word1.equals(word2.substring(0, word2.length() - 1))) {
					num_overlap++;
				}
			}
		}
		return num_overlap;
	}

	public static void incrementStringMapCount(Map<String, Integer> map, String key) {
		Integer cnt = map.get(key);
		if (cnt == null) {
			map.put(key, 1);
		} else {
			map.put(key, cnt.intValue() + 1);
		}
	}

	public static void incrementStringMapCount(Map<String, Integer> map, String key, int num) {
		Integer cnt = map.get(key);
		if (cnt == null) {
			map.put(key, num);
		} else {
			map.put(key, cnt.intValue() + num);
		}
	}

	public static void incrementStringDoubleMapCount(Map<String, Double> map, String key, double num) {
		Double cnt = map.get(key);
		if (cnt == null) {
			map.put(key, num);
		} else {
			map.put(key, cnt.doubleValue() + num);
		}
	}

	public static void incrementStringMapValueCount(Map<String, Map<String, Integer>> map, 
			String outer_key, String inner_key) {
		Map<String, Integer> inner_map = map.get(outer_key);
		if (inner_map == null) {
			inner_map = new HashMap<String, Integer>();
			map.put(outer_key, inner_map);
		}
		incrementStringMapCount(inner_map, inner_key);
	}

	public static void incrementStringDoubleMapValueCount(Map<String, Map<String, Double>> map, 
			String outer_key, String inner_key, double num) {
		Map<String, Double> inner_map = map.get(outer_key);
		if (inner_map == null) {
			inner_map = new HashMap<String, Double>();
			map.put(outer_key, inner_map);
		}
		incrementStringDoubleMapCount(inner_map, inner_key, num);
	}

	public static void incrementStringIntMapValueCount(Map<String, Map<String, Integer>> map, 
			String outer_key, String inner_key, int num) {
		Map<String, Integer> inner_map = map.get(outer_key);
		if (inner_map == null) {
			inner_map = new HashMap<String, Integer>();
			map.put(outer_key, inner_map);
		}
		incrementStringMapCount(inner_map, inner_key, num);
	}

	public static void incrementIntMapValueCount(Map<String, Map<Integer, Integer>> map, 
			String outer_key, Integer inner_key) {
		Map<Integer, Integer> inner_map = map.get(outer_key);
		if (inner_map == null) {
			inner_map = new HashMap<Integer, Integer>();
			map.put(outer_key, inner_map);
		}
		Integer cnt = inner_map.get(inner_key);
		if (cnt == null) {
			inner_map.put(inner_key, 1);
		} else {
			inner_map.put(inner_key, cnt.intValue() + 1);
		}
	}

	public static String removeNonAlphaNumeric(String str) {
		return str.replaceAll("[^A-Za-z0-9 ]", "");
	}

	private static String[] FINAL_SENTENCE_PUNC = new String[]{"!", "?", ";", ":", "*NL*", "\n", "...", "+", "="};
	public static List<String> splitStepSentences(File input_file) {
		DocumentPreprocessor dp = new DocumentPreprocessor(input_file.getAbsolutePath());

		dp.setSentenceFinalPuncWords(FINAL_SENTENCE_PUNC);
		// Set the tokenizer to assume that carriage returns are automatic sentence ends.
		//tokenizeNLs=true
		dp.setTokenizerFactory(PTBTokenizer.factory(new CoreLabelTokenFactory(), ""));
		List<List<HasWord>> sentences = new ArrayList<List<HasWord>>();
		for (List<HasWord> sentence : dp) {
			int num_words = sentence.size();
			int curr_begin = 0;
			System.out.println(StringUtils.join(sentence));
			for (int w = 0; w < num_words; w++) {
				HasWord word = sentence.get(w);
				if (word.word().endsWith(".") && !Measurements.isAmount(word.word())
						&& (w == 0 || !Measurements.isAmount(sentence.get(w - 1).word()))) {
					if ((w != num_words - 1 && Character.isUpperCase(sentence.get(w + 1).word().charAt(0)))
							|| (word.word().equals("."))) {
						List<HasWord> new_sentence = sentence.subList(curr_begin, w + 1);
						if ((new_sentence.size() != 0 && word.word().length() > 1) || (new_sentence.size() > 1 && word.word().equals("."))) {
							sentences.add(new_sentence);
						}
						curr_begin = w + 1;
					}
				}
			}
			List<HasWord> new_sentence = sentence.subList(curr_begin, num_words);
			if (new_sentence.size() != 0) {
				sentences.add(new_sentence);
			}

		}
		List<String> sent_strings = new ArrayList<String>();
		for (List<HasWord> sent : sentences) {
			String original_sentence_string = StringUtils.join(sent);
			sent_strings.add(original_sentence_string);
		}
		return sent_strings;
	}

	public static List<String> splitStepSentences(String str) {
		DocumentPreprocessor dp = new DocumentPreprocessor(new StringReader(str));

		dp.setSentenceFinalPuncWords(FINAL_SENTENCE_PUNC);
		// Set the tokenizer to assume that carriage returns are automatic sentence ends.
		//tokenizeNLs=true
		dp.setTokenizerFactory(PTBTokenizer.factory(new CoreLabelTokenFactory(), ""));
		List<List<HasWord>> sentences = new ArrayList<List<HasWord>>();
		for (List<HasWord> sentence : dp) {
			int num_words = sentence.size();
			int curr_begin = 0;
			for (int w = 0; w < num_words; w++) {
				HasWord word = sentence.get(w);
				if (word.word().equals(".") && w == 0) {
					List<HasWord> new_sentence = sentence.subList(curr_begin, w + 1);
					if ((new_sentence.size() != 0 && word.word().length() > 1) || (new_sentence.size() > 1 && word.word().equals("."))) {
						sentences.add(new_sentence);
					}
					curr_begin = w + 1;
				} else if (word.word().equals(".") && 
						!Measurements.isAmount(sentence.get(w - 1).word() + ".")
						&& !Measurements.isTime(sentence.get(w - 1).word() + ".")) {
					//					if ((w != num_words - 1 && Character.isUpperCase(sentence.get(w + 1).word().charAt(0)))) {
					////							|| (word.word().equals("."))) {
					List<HasWord> new_sentence = sentence.subList(curr_begin, w + 1);
					if ((new_sentence.size() != 0 && word.word().length() > 1) || (new_sentence.size() > 1 && word.word().equals("."))) {
						sentences.add(new_sentence);
					}
					curr_begin = w + 1;
					//					}
				} else 
					if (w != num_words - 1 && Character.isUpperCase(sentence.get(w + 1).word().charAt(0))
					&& word.word().equals(".")) {
						List<HasWord> new_sentence = sentence.subList(curr_begin, w + 1);
						if ((new_sentence.size() != 0 && word.word().length() > 1) || (new_sentence.size() > 1 && word.word().equals("."))) {
							sentences.add(new_sentence);
						}
						curr_begin = w + 1;
					}
			}
			List<HasWord> new_sentence = sentence.subList(curr_begin, num_words);
			if (new_sentence.size() != 0) {
				sentences.add(new_sentence);
			}

		}
		List<String> sent_strings = new ArrayList<String>();
		for (List<HasWord> sent : sentences) {
			String original_sentence_string = StringUtils.join(sent);
			sent_strings.add(original_sentence_string);
		}
		return sent_strings;
	}


	public static List<File[]> getDevFileList() {
		List<File[]> arg_and_fulltext_files = new ArrayList<File[]>();

		for (String category : ProjectParameters.ALL_RECIPE_TYPES) {
			System.out.println(category);
			String step_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.STEP_SUFFIX + "/";
			List<File> step_files = Utils.getInputFiles(step_directory_name, "txt");

			String arg_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.CHUNKED_SUFFIX + "/";
			List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");


			String fulltext_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
			List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

			String ann_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + "AnnotationSession" + "/" + "AnnotationSession" + "-splitann/" + category + "/";
			File ann_directory = new File(ann_directory_name);
			if (!ann_directory.exists()) {
				System.out.println("doesn't exist " + ann_directory_name);
				continue;
			}

			List<File> ann_files = Utils.getInputFiles(ann_directory_name, "ann");

			int j = 0;
			//			for (int s = 0; s < 2; s++) {
			for (int i = 0; i < ann_files.size(); i++) {
				File arg_file = null;
				File ann_file = null;
				File step_file = null;
				File fulltext_file = null;
				String recipe_name = null;
				while (true) {
					arg_file = arg_files.get(j);
					ann_file = ann_files.get(i);
					fulltext_file = fulltext_files.get(j);
					step_file = step_files.get(j);
					int suffix = arg_file.getName().lastIndexOf('.');
					recipe_name = arg_file.getName().substring(0, suffix);
					suffix = ann_file.getName().lastIndexOf('.');
					if (!recipe_name.equals(ann_file.getName().substring(0, suffix))) {
						j++;
					} else {
						break;
					}
				}
				File[] files = new File[4];
				files[0] = ann_file;
				files[1] = arg_file;
				files[2] = step_file;
				files[3] = fulltext_file;
				arg_and_fulltext_files.add(files);
			}
		}

		return arg_and_fulltext_files;
	}


	public static List<Pair<File, File>> getFileList(String category) {
		List<Pair<File, File>> arg_and_fulltext_files = new ArrayList<Pair<File, File>>();

		HashSet<String> recipe_blacklist = new HashSet<String>();
		String line;
		try{
			recipe_blacklist = new HashSet<String>();
			BufferedReader br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "devset_blacklist.txt"));
			while ((line = br.readLine()) != null) {
				recipe_blacklist.add(line);
			}
			br.close();
			br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "testset_blacklist.txt"));
			while ((line = br.readLine()) != null) {
				recipe_blacklist.add(line);
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		String arg_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.CHUNKED_SUFFIX + "/";
		List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");

		String fulltext_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
		List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

		for (int i = 0; i < arg_files.size(); i++) {
			File arg_file = arg_files.get(i);
			File fulltext_file = fulltext_files.get(i);
			if (!recipe_blacklist.contains(fulltext_file.getName())) {
				arg_and_fulltext_files.add(new Pair<File, File>(arg_file, fulltext_file));
			}
		}

		return arg_and_fulltext_files;
	}


	public static List<Pair<File, File>> getFileList() {
		List<Pair<File, File>> arg_and_fulltext_files = new ArrayList<Pair<File, File>>();

		HashSet<String> recipe_blacklist = new HashSet<String>();
		String line;
		try{
			recipe_blacklist = new HashSet<String>();
			BufferedReader br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "devset_blacklist.txt"));
			while ((line = br.readLine()) != null) {
				recipe_blacklist.add(line);
			}
			br.close();
			br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "testset_blacklist.txt"));
			while ((line = br.readLine()) != null) {
				recipe_blacklist.add(line);
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		for (String category : ProjectParameters.ALL_RECIPE_TYPES) {
			System.out.println(category);
			//			if (!category.equals("BananaMuffins")) {
			//				continue;
			//			}
			String arg_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.CHUNKED_SUFFIX + "/";
			List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");

			String fulltext_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
			List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

			for (int i = 0; i < arg_files.size(); i++) {
				File arg_file = arg_files.get(i);
				File fulltext_file = fulltext_files.get(i);
				if (!recipe_blacklist.contains(fulltext_file.getName())) {
					arg_and_fulltext_files.add(new Pair<File, File>(arg_file, fulltext_file));
				}
			}
		}

		return arg_and_fulltext_files;
	}

	public static List<Pair<File, File>> getStepFileList() {
		List<Pair<File, File>> arg_and_fulltext_files = new ArrayList<Pair<File, File>>();

		HashSet<String> recipe_blacklist = new HashSet<String>();
		String line;
		try{
			recipe_blacklist = new HashSet<String>();
			BufferedReader br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "devset_blacklist.txt"));
			while ((line = br.readLine()) != null) {
				recipe_blacklist.add(line);
			}
			br.close();
			br = new BufferedReader(new FileReader(ProjectParameters.DEFAULT_DATA_DIRECTORY + "testset_blacklist.txt"));
			while ((line = br.readLine()) != null) {
				recipe_blacklist.add(line);
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}

		for (String category : ProjectParameters.ALL_RECIPE_TYPES) {
			System.out.println(category);
			String arg_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.STEP_SUFFIX + "/";
			List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");

			String fulltext_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
			List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

			for (int i = 0; i < arg_files.size(); i++) {
				File arg_file = arg_files.get(i);
				File fulltext_file = fulltext_files.get(i);
				if (!recipe_blacklist.contains(fulltext_file.getName())) {
					arg_and_fulltext_files.add(new Pair<File, File>(arg_file, fulltext_file));
				}
			}
		}

		return arg_and_fulltext_files;
	}

	public static int wordOverlapCountUseTokenizer(String str1, String str2) {
		str1 = str1.toLowerCase();
		Pair<String, String> remove_amounts = Measurements.splitAmountString(str1);
		str1 = remove_amounts.getSecond();

		str2 = str2.toLowerCase();
		remove_amounts = Measurements.splitAmountString(str2);
		str2 = remove_amounts.getSecond();

		String[] split1 = str1.split(" ");
		String[] split2 = str2.split(" ");
		HashSet<String> words1 = new HashSet<String>();
		for (String word1 : split1) {
			if (TokenCounter.tokenCountGreaterThanOrEqualTo(word1, 1)) {
				// ignore certain words for now
				// TODO(chloe): make this more robust
				word1 = word1.toLowerCase();
				words1.add(word1);
			}
		}
		int num_overlap = 0;
		for (String word2 : split2) {
			word2 = word2.toLowerCase();
			for (String word1 : words1) {
				if (word1.equals(word2)) {
					num_overlap++;
				} else if (word2.equals(word1)) {
					num_overlap++;
				} else if (word1.endsWith("s") && word2.equals(word1.substring(0, word1.length() - 1))) {
					num_overlap++;
				} else if (word2.endsWith("s") && word1.equals(word2.substring(0, word2.length() - 1))) {
					num_overlap++;
				}
			}
		}
		return num_overlap;
	}

	public static int wordOverlapCountUseNouns(String str1, String str2) {
		str1 = str1.toLowerCase();
		Pair<String, String> remove_amounts = Measurements.splitAmountString(str1);
		str1 = remove_amounts.getSecond();

		str2 = str2.toLowerCase();
		remove_amounts = Measurements.splitAmountString(str2);
		str2 = remove_amounts.getSecond();

		String[] split1 = str1.split(" ");
		String[] split2 = str2.split(" ");
		HashSet<String> words1 = new HashSet<String>();
		for (String word1 : split1) {
			word1 = word1.replaceAll("[^A-Za-z0-9]","");
			word1 = word1.toLowerCase();
			if (TokenCounter.nouns_.contains(word1)) {
				// ignore certain words for now
				// TODO(chloe): make this more robust
				words1.add(word1);
			}
		}
		int num_overlap = 0;
		for (String word2 : split2) {
			word2 = word2.toLowerCase();
			word2 = word2.replaceAll("[^A-Za-z0-9]","");
			for (String word1 : words1) {
				if (word1.equals(word2)) {
					num_overlap++;
				} else if (word2.equals(word1)) {
					num_overlap++;
				} else if (word1.endsWith("s") && word2.equals(word1.substring(0, word1.length() - 1))) {
					num_overlap++;
				} else if (word2.endsWith("s") && word1.equals(word2.substring(0, word2.length() - 1))) {
					num_overlap++;
				}
			}
		}
		return num_overlap;
	}

	/**
	 * Just like wordOverlapCount except this returns the strings that overlap, not just the count.
	 * @param str1
	 * @param str2
	 * @return
	 */
	public static Pair<List<String>, List<String>> getWordOverlapUseTokenizer(String str1, String str2) {
		List<String> partial_overlap = new ArrayList<String>();
		List<String> overlap = new ArrayList<String>();
		str1 = str1.toLowerCase();
		Pair<String, String> remove_amounts = Measurements.splitAmountString(str1);
		str1 = remove_amounts.getSecond();

		str2 = str2.toLowerCase();
		remove_amounts = Measurements.splitAmountString(str2);
		str2 = remove_amounts.getSecond();

		String[] split1 = str1.split(" ");
		String[] split2 = str2.split(" ");

		HashSet<String> words1 = new HashSet<String>();
		for (String word1 : split1) {
			word1 = word1.toLowerCase();
			word1 = word1.replaceAll("[^A-Za-z0-9]","");
			//			System.out.println(word1 + " " + TokenCounter.token_counts_.get(word1));
			if (TokenCounter.tokenCountGreaterThanOrEqualTo(word1, 1)) {
				// ignore certain words for now
				// TODO(chloe): make this more robust
				words1.add(word1);
			}
		}
		for (String word2 : split2) {
			word2 = word2.toLowerCase();
			word2 = word2.replaceAll("[^A-Za-z0-9]","");
			for (String word1 : words1) {
				if (word1.equals(word2)) {
					overlap.add(word2);
				} else if (word2.equals(word1)) {
					overlap.add(word1);
				} else if (word1.endsWith("s") && word2.equals(word1.substring(0, word1.length() - 1))) {
					overlap.add(word1.substring(0, word1.length() - 1));
				} else if (word2.endsWith("s") && word1.equals(word2.substring(0, word2.length() - 1))) {
					overlap.add(word2.substring(0, word2.length() - 1));
				} else if (word1.startsWith(word2) && word2.length() >= 4) {
					partial_overlap.add(word2);
				} else if (word2.startsWith(word1) && word1.length() >= 4) {
					partial_overlap.add(word1);
				}
			}
		}
		return new Pair<List<String>, List<String>>(overlap, partial_overlap);
	}

	public static Pair<List<String>, List<String>> getWordOverlapUseWordNet(String str1, String str2) {
		List<String> partial_overlap = new ArrayList<String>();
		List<String> overlap = new ArrayList<String>();
		str1 = str1.toLowerCase();
		Pair<String, String> remove_amounts = Measurements.splitAmountString(str1);
		str1 = remove_amounts.getSecond();

		str2 = str2.toLowerCase();
		remove_amounts = Measurements.splitAmountString(str2);
		str2 = remove_amounts.getSecond();

		String[] split1 = str1.split(" ");
		String[] split2 = str2.split(" ");

		HashSet<String> words1 = new HashSet<String>();
		for (String word1 : split1) {
			if (word1.length() < 3) {
				continue;
			}
			word1 = word1.toLowerCase();
			word1 = word1.replaceAll("[^A-Za-z0-9]","");
			//			System.out.println(word1 + " " + TokenCounter.token_counts_.get(word1));
			if (Utils.canWordBeNounOrAdjOrVerbInWordNet(word1)) {
				// ignore certain words for now
				// TODO(chloe): make this more robust
				words1.add(word1);
			}
		}
		for (String word2 : split2) {
			if (word2.length() < 3) {
				continue;
			}
			word2 = word2.toLowerCase();
			word2 = word2.replaceAll("[^A-Za-z0-9]","");
			for (String word1 : words1) {
				if (word1.equals(word2)) {
					overlap.add(word2);
				} else if (word2.equals(word1)) {
					overlap.add(word1);
				} else if (word1.endsWith("s") && word2.equals(word1.substring(0, word1.length() - 1))) {
					overlap.add(word1.substring(0, word1.length() - 1));
				} else if (word2.endsWith("s") && word1.equals(word2.substring(0, word2.length() - 1))) {
					overlap.add(word2.substring(0, word2.length() - 1));
				} else if (word1.startsWith(word2) && word2.length() >= 4) {
					partial_overlap.add(word2);
				} else if (word2.startsWith(word1) && word1.length() >= 4) {
					partial_overlap.add(word1);
				}
			}
		}
		return new Pair<List<String>, List<String>>(overlap, partial_overlap);
	}

	/**
	 * Just like wordOverlapCount except this returns the strings that overlap, not just the count.
	 * @param str1
	 * @param str2
	 * @return
	 */
	public static List<String> getWordOverlap(String str1, String str2) {
		List<String> overlap = new ArrayList<String>();
		str1 = str1.toLowerCase();
		Pair<String, String> remove_amounts = Measurements.splitAmountString(str1);
		str1 = remove_amounts.getSecond();
		Pair<List<String>, List<String>> tagsAndTokens = lemmatizer_.tagAndTokenize(str1);
		String new_ingredient_str = "";
		List<String> tags = tagsAndTokens.getFirst();
		List<String> tokens = tagsAndTokens.getSecond();
		for (int t = 0; t < tokens.size(); t++) {
			if ((tags.get(t).startsWith("N") || tags.get(t).startsWith("V") || tags.get(t).startsWith("J"))) {
				new_ingredient_str += tokens.get(t) + " ";
			}
		}
		str1 = new_ingredient_str;

		str2 = str2.toLowerCase();
		remove_amounts = Measurements.splitAmountString(str2);
		str2 = remove_amounts.getSecond();
		tagsAndTokens = lemmatizer_.tagAndTokenize(str2);
		new_ingredient_str = "";
		tags = tagsAndTokens.getFirst();
		tokens = tagsAndTokens.getSecond();
		for (int t = 0; t < tokens.size(); t++) {
			if ((tags.get(t).startsWith("N") || tags.get(t).startsWith("V") || tags.get(t).startsWith("J"))) {
				new_ingredient_str += tokens.get(t) + " ";
			}
		}
		str2 = new_ingredient_str;

		String[] split1 = str1.split(" ");
		String[] split2 = str2.split(" ");
		HashSet<String> words1 = new HashSet<String>();
		for (String word1 : split1) {
			// ignore certain words for now
			// TODO(chloe): make this more robust
			word1 = word1.toLowerCase();
			words1.add(word1);
		}
		for (String word2 : split2) {
			word2 = word2.toLowerCase();
			for (String word1 : words1) {
				if (word1.equals(word2)) {
					overlap.add(word2);
				} else if (word2.equals(word1)) {
					overlap.add(word1);
				} else if (word1.endsWith("s") && word2.equals(word1.substring(0, word1.length() - 1))) {
					overlap.add(word1.substring(0, word1.length() - 1));
				} else if (word2.endsWith("s") && word1.equals(word2.substring(0, word2.length() - 1))) {
					overlap.add(word2.substring(0, word2.length() - 1));
				}
			}
		}
		return overlap;
	}

	/**
	 * Retrieves the set of all txt files in a given directory.
	 * 
	 * @param input_directory_name directory with recipes in plain text format
	 * @return list of recipe files
	 */
	public static List<File> getInputFiles(String input_directory_name, String suffix) {
		// read in input recipes
		File input_directory = new File(input_directory_name);
		if (!input_directory.exists()) {
			System.out.println("Input directory " + input_directory_name + " does not exist.");
			System.exit(1);
		}
		List<File> txt_input_files = new ArrayList<File>();
		for (File input_file : input_directory.listFiles()) {
			String full_file_name = input_file.getAbsolutePath();
			int period = full_file_name.lastIndexOf('.');
			if (period == -1) {
				continue;
			}
			String file_type = full_file_name.substring(period + 1);
			if (!file_type.equals(suffix)) {
				continue;
			}
			if (ProjectParameters.VERBOSE) {
				System.out.println(input_file.getAbsolutePath());
			}
			txt_input_files.add(input_file);
		}

		Collections.sort(txt_input_files);
		return txt_input_files;
	}

	public static double logsumexp(double[] exps) {
		double max = exps[0];
		for (double exp : exps) {
			if (exp > max) {
				max = exp;
			}
		}
		double sum = 0.0;
		for (double exp : exps) {
			sum += Math.exp(exp - max);
		}
		return max + Math.log(sum);
	}

	public static double logsumexp(double x1, double x2) {
		if (x1 > x2) {
			return x1 + Math.log(1 + Math.exp(x2 - x1));
		} else {
			return x2 + Math.log(1 + Math.exp(x1 - x2));
		}
	}

	/**
	 * Creates a directory if it does not already exist.
	 * 
	 * @param output_directory_name Name of directory to find or create.
	 */
	public static void createOutputDirectoryIfNotExist(String output_directory_name) {
		File output_directory = new File(output_directory_name);
		// If output directory does not exist, create it.
		if (!output_directory.exists()) {
			System.out.println("Creating directory: " + output_directory);
			try {
				output_directory.mkdir();
			} catch(SecurityException ex) {
				ex.printStackTrace();
				System.exit(1);
			}        
		}
	}

	public static boolean canWordBeVerbInWordNet(String word) {
		Synset[] synsets = database.getSynsets(word, SynsetType.VERB);
		if (synsets.length == 0) {
			return false;
		} else {
			return true;
		}

	}

	public static String[] getBaseNoun(String word) {
		return database.getBaseFormCandidates(word, SynsetType.NOUN);
	}

	public static String stem(String word) {
		return Stemmer.stemWord(word);
	}

	public static boolean canWordBeNounInWordNet(String word) {
		Synset[] synsets = database.getSynsets(word, SynsetType.NOUN);
		if (synsets.length == 0) {
			//			synsets = database.getSynsets(word);
			//			if (synsets.length == 0) {
			//				return true;
			//			}
			return false;
		} else {
			return true;
		}
	}

	public static boolean isSymbolOrWordInWordNet(String word) {
		String cleaned = word.replaceAll("[^-A-Za-z]", "");
		if (!cleaned.equals(word)) {
			return true;
		}
		Synset[] synsets = database.getSynsets(word);
		if (synsets.length == 0) {
			return false;
		}
		return true;
	}

	public static boolean isUpperCase(String word) {
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			if (Character.isLetter(c)) {
				if (Character.isLowerCase(c)) {
					return false;
				}
			}
		}
		return true;
	}

	public static boolean isMostlyNounInWordNet(String word) {
		Synset[] synsets = database.getSynsets(word);
		if (synsets.length == 0) {
			return true;
		}
		Synset[] n_synsets = database.getSynsets(word, SynsetType.NOUN);
		Synset[] v_synsets = database.getSynsets(word, SynsetType.VERB);
		Synset[] a_synsets = database.getSynsets(word, SynsetType.ADJECTIVE);
		Synset[] ad_synsets = database.getSynsets(word, SynsetType.ADVERB);
		//		System.out.println(word + " " + n_synsets.length + " " + a_synsets.length + ad_synsets.length);
		if (n_synsets.length != 0 && n_synsets.length > a_synsets.length && 
				n_synsets.length > ad_synsets.length) {// && n_synsets.length > v_synsets.length) {
			return true;
		}
		return false;
	}

	public static boolean isMostlyAdjectiveInWordNet(String word) {
		Synset[] synsets = database.getSynsets(word);
		if (synsets.length == 0) {
			return true;
		}
		Synset[] n_synsets = database.getSynsets(word, SynsetType.NOUN);
		Synset[] v_synsets = database.getSynsets(word, SynsetType.VERB);
		Synset[] a_synsets = database.getSynsets(word, SynsetType.ADJECTIVE);
		Synset[] ad_synsets = database.getSynsets(word, SynsetType.ADVERB);
		//		System.out.println(word + " " + n_synsets.length + " " + a_synsets.length + ad_synsets.length);
		if (a_synsets.length != 0 && a_synsets.length > n_synsets.length && 
				a_synsets.length > ad_synsets.length && a_synsets.length > v_synsets.length) {
			return true;
		}
		return false;
	}

	public static boolean isAdverb(String word) {
		Synset[] a_synsets = database.getSynsets(word, SynsetType.ADVERB);
		if (a_synsets.length != 0) {
			return true;
		}
		return false;
	}

	public static boolean canWordBeNounOrAdjInWordNet(String word) {
		Synset[] synsets = database.getSynsets(word, SynsetType.NOUN);
		if (synsets.length == 0) {
			synsets = database.getSynsets(word, SynsetType.ADJECTIVE);
			if (synsets.length == 0) {
				synsets = database.getSynsets(word);
				if (synsets.length == 0 && word.length() > 3) {
					return true;
				}
				return false;
			} else {
				return true;
			}
		} else {
			return true;
		}
	}

	public static boolean canWordBeNounOrAdjOrVerbInWordNet(String word) {
		return canWordBeNounOrAdjInWordNet(word) || canWordBeVerbInWordNet(word);
	}
	
	public static boolean goodVerb(String word) {
		Synset[] w = database.getSynsets(word, SynsetType.VERB);
		boolean is_first = false;
		for (Synset s : w) {
			String[] words = s.getWordForms();
			if (words[0].equals(word)) {
				is_first = true;
				break;
			}
		}
		if (!is_first) {
			for (Synset s : w) {

				String[] words = s.getWordForms();
				boolean all_zero = true;
				for (String word_form : words) {
					try {
						int cnt = s.getTagCount(word_form);
						if (cnt != 0) {
							all_zero = false;
							break;
						}
					} catch (Exception e) {

					}
				}
				try {
					int cnt = s.getTagCount(word);
					if (cnt != 0 || all_zero) {
						is_first = true;
						break;
					}
				} catch (Exception e) {
				}
			}
		}
		if (!is_first) {
			return false;
		} else {
			return true;
		}
	}

	public static void main(String[] args) {
		double[] arr = new double[]{2, 3, 200000};
		System.out.println(Utils.logsumexp(arr));
		System.out.println(Math.log(Math.exp(2) + Math.exp(3) + Math.exp(200000)));

		System.out.println(lemmatizer_.tag("flour"));
		System.out.println(lemmatizer_.tag("combine"));
		System.out.println(lemmatizer_.tag("salt"));
		System.out.println(lemmatizer_.tag("stir"));
		System.out.println(lemmatizer_.tag("whisk"));
		System.out.println(lemmatizer_.tag("add"));
		System.out.println(lemmatizer_.tag("place"));
		System.out.println(lemmatizer_.tag("chop"));
		System.out.println(lemmatizer_.tag("peel"));

		//		Synset[] synsets = database.getSynsets("sour", SynsetType.NOUN);
		//		if (synsets == null) {
		//			System.out.println("fly null");
		//		} else {
		//			System.out.println("fly " + synsets.length);
		//		}
		//
		//		synsets = database.getSynsets("sour", SynsetType.VERB);
		//		if (synsets == null) {
		//			System.out.println("microwave null");
		//		} else {
		//			System.out.println("microwave " + synsets.length);
		//		}
		//		synsets = database.getSynsets("sour", SynsetType.ADVERB);
		//		if (synsets == null) {
		//			System.out.println("evenly null");
		//		} else {
		//			System.out.println("evenly " + synsets.length);
		//		}
		//		synsets = database.getSynsets("sour", SynsetType.ADJECTIVE);
		//		if (synsets == null) {
		//			System.out.println("large null");
		//		} else {
		//			System.out.println("large " + synsets.length);
		//			for (Synset s : synsets) {
		//				System.out.println(s.getDefinition());
		//			}
		//		}
		//		System.out.println(Utils.canWordBeNounOrAdjInWordNet("the") || Utils.canWordBeVerbInWordNet("the"));
		//		String[] w = database.getBaseFormCandidates("potatos", SynsetType.NOUN);
		//		synsets = database.getSynsets("potatoes");
		//		for (Synset syn : synsets) {
		//			String[] words = syn.getWordForms();
		//			for (String q : words) {
		//				System.out.println(q);
		//			}
		//			System.out.println();
		//		}
		//		System.out.println();
		//		for (String s : w) {
		//			System.out.println(s);
		//		}
		//		System.out.println(stem("potatoes"));
		//		System.out.println(stem("bananas"));
		//		System.out.println(stem("mashed"));
		//		System.out.println(stem("mixture"));
		//		System.out.println(Utils.canWordBeNounOrAdjInWordNet("muffin"));
//		try {
//			BufferedReader br = new BufferedReader(new FileReader("/Users/chloe/Research/nyc_recipe_data/nyc_first_words_steps.txt"));
//			BufferedWriter bw = new BufferedWriter(new FileWriter("/Users/chloe/Research/nyc_recipe_data/nyc_first_words_steps_clean.txt"));
//			String line;
//			while ((line = br.readLine()) != null) {
//				String[] split = line.split("\t");
//				Synset[] w = database.getSynsets(split[0], SynsetType.VERB);
//				boolean is_first = false;
//				for (Synset s : w) {
//					String[] words = s.getWordForms();
//					if (words[0].equals(split[0])) {
//						is_first = true;
//						break;
//					}
//				}
//				if (!is_first) {
//					for (Synset s : w) {
//
//						String[] words = s.getWordForms();
//						boolean all_zero = true;
//						for (String word : words) {
//							try {
//								int cnt = s.getTagCount(word);
//								if (cnt != 0) {
//									all_zero = false;
//									break;
//								}
//							} catch (Exception e) {
//
//							}
//						}
//						try {
//							int cnt = s.getTagCount(split[0]);
//							if (cnt != 0 || all_zero) {
//								is_first = true;
//								break;
//							}
//						} catch (Exception e) {
//						}
//					}
//				}
//				if (!is_first) {
//					System.out.println(line);
//				} else {
////					bw.write(line + "\n");
//				}
//			}
//			br.close();
//			bw.close();
//		} catch (IOException ex) {
//			ex.printStackTrace();
//		}
		Synset[] w = database.getSynsets("plain", SynsetType.VERB);
		for (Synset s : w) {
			String[] words = s.getWordForms();
			for (String a : words) {
				System.out.print(a + " ");
			}
			System.out.println();
		}
		System.out.println("plain " + goodVerb("apple"));
	}
}
