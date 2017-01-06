package data;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import preprocessing.Lemmatizer;
import utils.Measurements;
import utils.Pair;
import utils.Utils;

public class RecipeSentenceSegmenter {
	private static Lemmatizer lemmatizer = null;
	private Map<String, Integer> verb_counts;
	private int ttl_count;

	private Map<String, Map<Integer, Double>> verb_arg_num_probs;
	private Map<String, Map<String, Double>> arg_bigram_model;
	private Map<String, Double> verb_geometric_arg_num;
	private double num_verbs_geometric_num = -1;


	public static String END = "END";
	public static String OTHER = "OTHER";
	public static String PRED = "PRED";

	public static RecipeSentenceSegmenter readInFromFile(String filename) throws IOException {
		RecipeSentenceSegmenter chunker = new RecipeSentenceSegmenter();
		BufferedReader br = new BufferedReader(new FileReader(filename));
		chunker.ttl_count = Integer.parseInt(br.readLine());
		int size = Integer.parseInt(br.readLine());
		populateHashMap(br, size, chunker.verb_counts);

		chunker.num_verbs_geometric_num = Double.parseDouble(br.readLine());
		size = Integer.parseInt(br.readLine());
		chunker.verb_geometric_arg_num = new HashMap<String, Double>();
		populateHashMapDouble(br, size, chunker.verb_geometric_arg_num);
		size = Integer.parseInt(br.readLine());
		chunker.verb_arg_num_probs = new HashMap<String, Map<Integer, Double>>();
		for (int i = 0; i < size; i++) {
			String verb = br.readLine();
			Map<Integer, Double> probs = new HashMap<Integer, Double>();
			probs.put(0, Double.parseDouble(br.readLine()));
			probs.put(1, Double.parseDouble(br.readLine()));
			probs.put(2, Double.parseDouble(br.readLine()));
			probs.put(3, Double.parseDouble(br.readLine()));
			chunker.verb_arg_num_probs.put(verb, probs);
		}
		size = Integer.parseInt(br.readLine());
		chunker.arg_bigram_model = new HashMap<String, Map<String, Double>>();
		for (int i = 0; i < size; i++) {
			String first = br.readLine();
			HashMap<String, Double> bigram_map = new HashMap<String, Double>();
			int size2 = Integer.parseInt(br.readLine());
			for (int j = 0; j < size2; j++) {
				String[] split = br.readLine().split("\t");
				bigram_map.put(split[0], Double.parseDouble(split[1]));
			}
			chunker.arg_bigram_model.put(first, bigram_map);
		}
		br.close();
		return chunker;
	}

	private static void populateHashMap(BufferedReader br, int num_entries, Map<String, Integer> map) throws IOException {
		for (int i = 0; i < num_entries; i++) {
			String line = br.readLine();
			String[] split = line.split("\t");
			try {
				map.put(split[0], Integer.parseInt(split[1]));
			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println(line);
				System.exit(1);
			}
		}
	}

	private static void populateHashMapDouble(BufferedReader br, int num_entries, Map<String, Double> map) throws IOException {
		for (int i = 0; i < num_entries; i++) {
			String line = br.readLine();
			String[] split = line.split("\t");
			try {
				map.put(split[0], Double.parseDouble(split[1]));
			} catch (Exception ex) {
				ex.printStackTrace();
				System.out.println(line);
				System.exit(1);
			}
		}
	}

	public void writeToFile(String filename) throws IOException {
		BufferedWriter bw = new BufferedWriter(new FileWriter(filename));
		bw.write(ttl_count + "\n");
		bw.write(verb_counts.size() + "\n");
		for (String verb : verb_counts.keySet()) {
			bw.write(verb + "\t" + verb_counts.get(verb) + "\n");
		}

		bw.write(num_verbs_geometric_num + "\n");
		bw.write(verb_geometric_arg_num.size() + "\n");
		for (String verb : verb_geometric_arg_num.keySet()) {
			bw.write(verb + "\t" + verb_geometric_arg_num.get(verb) + "\n");
		}
		bw.write(verb_arg_num_probs.size() + "\n");
		for (String verb : verb_arg_num_probs.keySet()) {
			Map<Integer, Double> probs = verb_arg_num_probs.get(verb);
			bw.write(verb + "\n");
			for (int i = 0; i <= 3; i++) {
				bw.write(probs.get(i) + "\n");
			}
		}
		bw.write(arg_bigram_model.size() + "\n");
		for (String first : arg_bigram_model.keySet()) {
			bw.write(first + "\n");
			Map<String, Double> bigram_cnt = arg_bigram_model.get(first);
			bw.write(bigram_cnt.size() + "\n");
			for (String second : bigram_cnt.keySet()) {
				bw.write(second + "\t" + bigram_cnt.get(second) + "\n");
			}
		}
		bw.close();
	}

	public void setArgBigramProb(String first, String second, Double prob) {
		if (arg_bigram_model == null) {
			arg_bigram_model = new HashMap<String, Map<String, Double>>();
		}
		Map<String, Double> bigram = arg_bigram_model.get(first);
		if (bigram == null) {
			bigram = new HashMap<String, Double>();
			arg_bigram_model.put(first, bigram);
		}
		bigram.put(second, prob);
	}

	public void setVerbArgNumProb(String verb, Integer cnt, Double prob) {
		if (verb_arg_num_probs == null) {
			verb_arg_num_probs = new HashMap<String, Map<Integer, Double>>();
		}
		Map<Integer, Double> map = verb_arg_num_probs.get(verb);
		if (map == null) {
			map = new HashMap<Integer, Double>();
			verb_arg_num_probs.put(verb, map);
		}
		map.put(cnt, prob);
	}

	public void setVerbGeo(String verb, Double num) {
		if (verb_geometric_arg_num == null) {
			verb_geometric_arg_num = new HashMap<String, Double>();
		}
		verb_geometric_arg_num.put(verb, num);
	}

	public void setSentGeo(Double num) {
		num_verbs_geometric_num = num;
	}

	public void setVerbCount(String verb, Integer cnt) {
		verb_counts.put(verb, cnt);
	}

	public void setTotalVerbsCount(int cnt) {
		ttl_count = cnt;
	}

	public static Set<String> getSentences(File arg_file) throws IOException {
		Set<String> sentences = new HashSet<String>();
		BufferedReader br = new BufferedReader(new FileReader(arg_file));
		String line;
		while ((line = br.readLine()) != null) {
			if (line.length() == 0) {
				continue;
			}
			int colon_idx = line.indexOf(':');
			String type = line.substring(0, colon_idx).trim();
			String data = line.substring(colon_idx + 2);
			if (type.equals("SENT")) {
				sentences.add(data);
			}
		}
		br.close();
		return sentences;
	}

	public List<Pair<String, List<Pair<String, List<String>>>>> getPredArgStructure(File step_file) throws IOException {
		List<Pair<String, List<Pair<String, List<String>>>>> pred_args = 
				new ArrayList<Pair<String, List<Pair<String, List<String>>>>>();
		List<String> sentences = Utils.splitStepSentences(step_file);
		for (String sentence : sentences) {
			sentence = sentence.replaceAll("\\*", "");
			sentence = sentence.replaceAll("  ", "");
			List<Pair<String, List<String>>> sent_pred_args = new ArrayList<Pair<String, List<String>>>();
			String[] words = sentence.split(" ");
			List<String> best_tags = getMostProbableSequence(words);
			if (best_tags == null) {
				continue;
			}

			sent_pred_args.addAll(extractPredArgStructures(words, best_tags));
			pred_args.add(new Pair<String, List<Pair<String, List<String>>>>(sentence, sent_pred_args));
		}
		return pred_args;
	}

	private void readInVerbCounts(String verb_filename) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(verb_filename));
			String line;
			while ((line = br.readLine()) != null) {
				String[] split = line.split("\t");
				String word = split[0];
				int cnt = Integer.parseInt(split[1]);
				ttl_count+=cnt;
				verb_counts.put(word, cnt);
			}
			br.close();
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	public RecipeSentenceSegmenter() {
		if (lemmatizer == null) {
			lemmatizer = new Lemmatizer();
		}
		verb_counts = new HashMap<String, Integer>();
		ttl_count = 0;
		//		only_verbs = new HashSet<String>();
		arg_bigram_model = null;
		verb_geometric_arg_num = null;
		verb_arg_num_probs = null;
	}


	public RecipeSentenceSegmenter(boolean use_initial, String verb_file_name) {
		this();
		if (use_initial) {
			readInVerbCounts(verb_file_name);
		}
	}


	public String getTag(String word) {
		List<String> tags = lemmatizer.tag(word);
		if (tags.size() == 0) {
			return "??";
		}
		return tags.get(0);
	}

	private static String PREDICATE = "PRED";
	private static String ARGUMENT = "ARG";

	public double logProbOfSequence(String[] words, String[] tags) {
		double lprob = 0.0;
		String curr_verb = null;
		int num_prep_args = 0;
		boolean obj = false;
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			String tag = tags[i];
			if (tag.equals(PREDICATE)) {
				Integer pred_cnt = verb_counts.get(word);
				lprob += Math.log(pred_cnt) - Math.log(ttl_count);
			}
		}
		return lprob;
	}

	public double logProbOfSequence(String[] words, List<String> tags) {
		double lprob = 0.0;
		String curr_verb = null;
		int num_prep_args = 0;
		boolean obj = false;
		for (int i = 0; i < words.length; i++) {
			String word = words[i].toLowerCase();
			String tag = tags.get(i);
			if (tag.equals(PREDICATE)) {
				Integer pred_cnt = verb_counts.get(word);
				lprob += Math.log(pred_cnt) - Math.log(ttl_count);
			}
		}
		return lprob;
	}

	private static double geometric(double p, int k) {
		return Math.pow(1.0 - p, k - 1) * p;
	}

//	public double logProbOfPredArgSet(Pair<String, Set<String>> pair) {
//		double lprob = 0.0;
//		if (arg_bigram_model == null) {
//			String pred = pair.getFirst();
//			Integer pred_cnt = verb_counts.get(pred);
//			lprob += Math.log(pred_cnt) - Math.log(ttl_count);
//		} else {
//			String pred = pair.getFirst();
//			String predstart = PRED + pred;
//			Set<String> args = pair.getSecond();
//			Integer pred_cnt = verb_counts.get(pred);
//			lprob += Math.log(pred_cnt) - Math.log(ttl_count);
//
//			//			Double geo = verb_geometric_arg_num.get(pred);
//			//			if (geo != null) {
//			//				lprob += Math.log(geometric(geo, args.size()));
//			//			} else {
//			//				lprob += Math.log(geometric(0.5, args.size()));
//			//			}
//			int arg_size = args.size();
//			if (arg_size > 3) {
//				arg_size = 3;
//			}
//			Map<Integer, Double> probs = verb_arg_num_probs.get(pred);
//			if (probs == null) {
//				lprob += Math.log(0.25);
//			} else {
//				lprob += probs.get(arg_size);
//			}
//
//			for (String arg : args) {
//				String[] split = arg.split(" ");
//				for (int i = 0; i < split.length; i++) {
//					String tkn = split[i];
//					if (Measurements.isNumericString(tkn)) {
//						tkn = "NUM#";
//					}
//					if (i == 0) {
//						Map<String, Double> bigram_probs = arg_bigram_model.get(predstart);
//						Double prob = bigram_probs.get(tkn);
//						if (prob == null) {
//							prob = bigram_probs.get(OTHER);
//						}
//						lprob += prob;
//					} else {
//						String prev = split[i - 1];
////						int j = i - 1;
////						while (prev.matches("and|,") && j > 0) {
////							j--;
////							prev = split[j];
////						}
//						if (prev.matches("and|,")) {
//							prev = predstart;
//						}
//						if (Measurements.isNumericString(prev)) {
//							prev = "NUM#";
//						}
//						Map<String, Double> bigram_probs = arg_bigram_model.get(prev);
//						if (bigram_probs == null) {
//							bigram_probs = arg_bigram_model.get(OTHER);
//						}
//						Double prob = bigram_probs.get(tkn);
//						if (prob == null) {
//							prob = bigram_probs.get(OTHER);
//						}
//						lprob += prob;
//					}
//				}
//				String prev = split[split.length - 1];
//				Map<String, Double> bigram_probs = arg_bigram_model.get(prev);
//				if (bigram_probs == null) {
//					bigram_probs = arg_bigram_model.get(OTHER);
//				}
//				Double prob = bigram_probs.get(END);
//				if (prob == null) {
//					prob = bigram_probs.get(OTHER);
//				}
//				lprob += prob;
//			}
//		}
//		return lprob;
//	}

	public double logProbOfPred(Pair<String, List<String>> pair) {
		double lprob = 0.0;
		if (arg_bigram_model == null) {
			String pred = pair.getFirst();
			Integer pred_cnt = verb_counts.get(pred);
			lprob += Math.log(pred_cnt) - Math.log(ttl_count);
		} else {
			String pred = pair.getFirst();
			String predstart = PRED + pred;
			List<String> args = pair.getSecond();
			Integer pred_cnt = verb_counts.get(pred);
			lprob += Math.log(pred_cnt) - Math.log(ttl_count);
//			System.out.println(pred + " " + (Math.log(pred_cnt) - Math.log(ttl_count)));

			//			Double geo = verb_geometric_arg_num.get(pred);
			//			if (geo != null) {
			//				lprob += Math.log(geometric(geo, args.size()));
			//			} else {
			//				lprob += Math.log(geometric(0.5, args.size()));
			//			}
			
			int arg_size = args.size();
			if (arg_size > 3) {
				arg_size = 3;
			}
			Map<Integer, Double> probs = verb_arg_num_probs.get(pred);
			if (probs == null) {
				lprob += Math.log(0.25);
			} else {
				lprob += probs.get(arg_size);
//				System.out.println(arg_size + " " + probs.get(arg_size));
			}

			for (String arg : args) {
				String[] split = arg.split(" ");
				for (int i = 0; i < split.length; i++) {
					String tkn = split[i];
					if (Measurements.isNumericString(tkn)) {
						tkn = "NUM#";
					}
					if (i == 0) {
						Map<String, Double> bigram_probs = arg_bigram_model.get(predstart);
						if (bigram_probs == null) {
							bigram_probs = arg_bigram_model.get(OTHER);
//							System.out.print(OTHER + " ");
						} else {
//							System.out.print(predstart + " ");
						}
						if (tkn.matches("and|,")) {
							continue;
//							tkn = END;
						}
						Double prob = bigram_probs.get(tkn);
						if (prob == null) {
							prob = bigram_probs.get(OTHER);
//							System.out.print(OTHER + " ");
						} else {
//							System.out.print(tkn + " ");
						}
						lprob += prob;
//						System.out.println(prob);
					} else {
						String prev = split[i - 1];
//						int j = i - 1;
//						while (prev.matches("and|,") && j > 0) {
//							j--;
//							prev = split[j];
//						}
						if (prev.matches("and|,")) {
							prev = predstart;
						}
						if (Measurements.isNumericString(prev)) {
							prev = "NUM#";
						}
						
						Map<String, Double> bigram_probs = arg_bigram_model.get(prev);
						if (bigram_probs == null) {
							bigram_probs = arg_bigram_model.get(OTHER);
//							System.out.print(OTHER + " ");
						} else {
//							System.out.print(prev + " ");
						}
						if (tkn.matches("and|,")) {
							continue;
//							tkn = END;
						}
						Double prob = bigram_probs.get(tkn);
						if (prob == null) {
							prob = bigram_probs.get(OTHER);
//							System.out.print(OTHER + " ");
						} else {
//							System.out.print(tkn + " ");
						}
						
						lprob += prob;
//						System.out.println(prob);
					}
				}
				String prev = split[split.length - 1];
				Map<String, Double> bigram_probs = arg_bigram_model.get(prev);
				if (bigram_probs == null) {
					bigram_probs = arg_bigram_model.get(OTHER);
//					System.out.print(OTHER + " ");
				} else {
//					System.out.print(prev + " ");
				}
				Double prob = bigram_probs.get(END);
				if (prob == null) {
					prob = bigram_probs.get(OTHER);
//					System.out.print(OTHER + " ");
				}
//				System.out.print(END + " ");
				lprob += prob;
//				System.out.println(prob);
			}
		}
		return lprob;
	}

	public double probOfNumPreds(int num_preds) {
		if (num_verbs_geometric_num != -1) {
			return Math.log(geometric(num_verbs_geometric_num, num_preds));
		}
		return 0.0;
	}

	public double logProbOfSequence(List<Pair<String, List<String>>> pred_args) {
		double lprob = 0.0;
		String curr_verb = null;
		int num_prep_args = 0;
		boolean obj = false;
		if (num_verbs_geometric_num != -1) {
			lprob += Math.log(geometric(num_verbs_geometric_num, pred_args.size()));
		}
		for (Pair<String, List<String>> pair : pred_args) {
			lprob += logProbOfPred(pair);
		}

		return lprob;
	}

	private Pair<String, Integer> createArgString(String[] words, List<String> tags, int start) {
		String arg;
		boolean in_rb = false;
		if (words[start].replaceAll("[^A-Za-z0-9]","").equals("")) {
			arg = "";
		} else if (words[start].toLowerCase().matches("-lrb-|-rrb-|-rsb-|-lsb-")) {
			arg = "";
		} else {
			arg = words[start] + " ";
			if (tags.get(start).equals("RB")) {
				in_rb = true;
				return new Pair<String, Integer>(arg.trim().toLowerCase(), start + 1);
			}
		}
		for (int i = start + 1; i < words.length; i++) {
			String tag = tags.get(i);
			if (tag.equals(PREDICATE) || tag.matches("IN|TO|RB|VBG") || tag.startsWith("W")) {
				if (tag.equals("VBG") && (i == words.length - 1 || !tags.get(i+1).matches("DT|CD"))) {
					arg += words[i] + " ";
					continue;
				}
				if (!tag.equals(PREDICATE) && words[start].equals("until")) {
					arg += words[i] + " ";
					continue;
				}
				if (tag.matches("IN|TO|RB") && tags.get(i-1).matches("IN|TO|DT|CD")) {
					arg += words[i] + " ";
					continue;
				}
				if (tag.equals("TO") && (tags.get(i-1).equals("CD") || Measurements.isNumericString(words[i-1]))) {
					arg += words[i] + " ";
					continue;
				}
				if (words[i].equals("of")) {
					arg += words[i] + " ";
					continue;
				}
				if (tags.get(i-1).equals("CC") || words[i-1].equals("then")) {
					int space = arg.trim().lastIndexOf(' ');
					if (space == -1) {
						return new Pair<String, Integer>(null, i);
					}
					arg = arg.trim().substring(0, space);
					if (i > 1 && tags.get(i-2).replaceAll("[^A-Za-z0-9]","").equals("")) {
						space = arg.trim().lastIndexOf(' ');
						if (space == -1) {
							return new Pair<String, Integer>(null, i);
						}
						return new Pair<String, Integer>(arg.substring(0, space).toLowerCase(), i);
					}
					return new Pair<String, Integer>(arg.substring(0, space).toLowerCase(), i);	
				}
				if (tags.get(i - 1).replaceAll("[^A-Za-z0-9]","").equals("")) {
					int space = arg.trim().lastIndexOf(' ');
					if (space == -1) {
						return new Pair<String, Integer>(null, i);
					}

					return new Pair<String, Integer>(arg.substring(0, space).toLowerCase(), i);
				}
				return new Pair<String, Integer>(arg.trim().toLowerCase(), i);
			}
			if (in_rb && tag.matches("DT|CD")) {
				if (tags.get(i - 1).replaceAll("[^A-Za-z0-9]","").equals("")) {
					int space = arg.trim().lastIndexOf(' ');
					if (space == -1) {
						return new Pair<String, Integer>(null, i);
					}

					return new Pair<String, Integer>(arg.substring(0, space).toLowerCase(), i);
				}
				return new Pair<String, Integer>(arg.trim().toLowerCase(), i);
			}
			arg += words[i] + " ";
		}
		if (tags.get(words.length - 1).replaceAll("[^A-Za-z0-9]","").equals("")) {
			int space = arg.trim().lastIndexOf(' ');
			if (space == -1) {
				return new Pair<String, Integer>(null, words.length);
			}
			return new Pair<String, Integer>(arg.substring(0, space).toLowerCase(), words.length);
		}
		return new Pair<String, Integer>(arg.trim().toLowerCase(), words.length);
	}

	public List<Pair<String, List<String>>> extractSequentialPredArgStructures(String[] words, List<String> tags) {
		List<Pair<String, List<String>>>  pred_args = new ArrayList<Pair<String, List<String>>>(); 
		int index = -1;
		String curr_verb = null;
		List<String> curr_args = new ArrayList<String>();
		boolean first = true;
		for (int i = 0; i < words.length; i++) {
			String tag = tags.get(i);
			if (tag.equals(PREDICATE)) {
				if (curr_verb == null) {
					curr_verb = words[i].toLowerCase();
					curr_args.add(curr_verb);
					index = i;
				} else {
					pred_args.add(new Pair<String, List<String>>(curr_verb, curr_args));
					curr_verb = words[i].toLowerCase();
					index = i;
					curr_args = new ArrayList<String>();
					curr_args.add(curr_verb);
					first = true;
				}
			} else {
				Pair<String, Integer> arg = createArgString(words, tags, i);
				if (arg.getFirst() != null) {
					int prev = 0;
					if (first) {
						first = false;
						for (int j = index - 1; j >= 0; j--) {
							String prev_tag = tags.get(j);
							if (prev_tag.equals(PREDICATE)) {
								prev++;
							}
							if (!prev_tag.matches("CC|RB")) {
								break;
							}
						}
					}
					// post emnlp 2015
//					if (prev == 0) {
					if (true || prev == 0) {
						curr_args.add(arg.getFirst());
					} else {
						pred_args.get(pred_args.size() - prev).getSecond().add(arg.getFirst());
					}
				}
				i = arg.getSecond() - 1; // minus 1 so that it increments to the next one in the loop
			}
		}
		//		String indexed_verb = Integer.toString(index) + ":" + curr_verb;
		pred_args.add(new Pair<String, List<String>>(curr_verb, curr_args));
		return pred_args;
	}
	
	public List<Pair<String, List<String>>> extractPredArgStructures(String[] words, List<String> tags) {
		List<Pair<String, List<String>>>  pred_args = new ArrayList<Pair<String, List<String>>>(); 
		int index = -1;
		String curr_verb = null;
		List<String> curr_args = new ArrayList<String>();
		boolean first = true;
		for (int i = 0; i < words.length; i++) {
			String tag = tags.get(i);
			if (tag.equals(PREDICATE)) {
				if (curr_verb == null) {
					curr_verb = words[i].toLowerCase();
					index = i;
					//					if (curr_args.size() > 1) {
					//						return null;
					//					}
				} else {
					pred_args.add(new Pair<String, List<String>>(curr_verb, curr_args));
					curr_verb = words[i].toLowerCase();
					index = i;
					curr_args = new ArrayList<String>();
					first = true;
				}
			} else {
				Pair<String, Integer> arg = createArgString(words, tags, i);
				if (arg.getFirst() != null) {
					int prev = 0;
					if (first) {
						first = false;
						for (int j = index - 1; j >= 0; j--) {
							String prev_tag = tags.get(j);
							if (prev_tag.equals(PREDICATE)) {
								prev++;
							}
							if (!prev_tag.matches("CC|RB")) {
								break;
							}
						}
					}
					if (prev == 0) {
						curr_args.add(arg.getFirst());
					} else {
						pred_args.get(pred_args.size() - prev).getSecond().add(arg.getFirst());
					}
				}
				i = arg.getSecond() - 1; // minus 1 so that it increments to the next one in the loop
			}
		}
		//		String indexed_verb = Integer.toString(index) + ":" + curr_verb;
		pred_args.add(new Pair<String, List<String>>(curr_verb, curr_args));
		return pred_args;
	}
	
	public List<Pair<String, List<String>>> extractPredArgStructuresLC(String[] words, List<String> tags) {
		List<Pair<String, List<String>>>  pred_args = new ArrayList<Pair<String, List<String>>>(); 
		int index = -1;
		String curr_verb = null;
		List<String> curr_args = new ArrayList<String>();
		boolean first = true;
		for (int i = 0; i < words.length; i++) {
			String tag = tags.get(i);
			if (tag.equals(PREDICATE)) {
				if (curr_verb == null) {
					curr_verb = words[i];
					index = i;
					//					if (curr_args.size() > 1) {
					//						return null;
					//					}
				} else {
					pred_args.add(new Pair<String, List<String>>(curr_verb, curr_args));
					curr_verb = words[i];
					index = i;
					curr_args = new ArrayList<String>();
					first = true;
				}
			} else {
				Pair<String, Integer> arg = createArgString(words, tags, i);
				if (arg.getFirst() != null) {
					int prev = 0;
					if (first) {
						first = false;
						for (int j = index - 1; j >= 0; j--) {
							String prev_tag = tags.get(j);
							if (prev_tag.equals(PREDICATE)) {
								prev++;
							}
							if (!prev_tag.matches("CC|RB")) {
								break;
							}
						}
					}
					if (prev == 0) {
						curr_args.add(arg.getFirst());
					} else {
						pred_args.get(pred_args.size() - prev).getSecond().add(arg.getFirst());
					}
				}
				i = arg.getSecond() - 1; // minus 1 so that it increments to the next one in the loop
			}
		}
		//		String indexed_verb = Integer.toString(index) + ":" + curr_verb;
		pred_args.add(new Pair<String, List<String>>(curr_verb, curr_args));
		return pred_args;
	}

	private boolean doesArgHaveNoun(String arg) {
		String[] split = arg.split(" ");
		for (String s : split) {
			String tag = getTag(s.toLowerCase());
			if (tag.matches("RB") || s.equals("through")) {
				return true;
			}
			if (tag.matches("IN|TO")) {
				continue;
			}
			if (Utils.canWordBeNounInWordNet(s) || Utils.canWordBeVerbInWordNet(s)) {
				return true;
			}
		}
		return false;
	}

	public void getPossibleSequences(String[] words, 
			String[] tags, int start, List<String> seq, Set<List<String>> seqs) {
		if (start == words.length) {
			//			for (int i = 0; i < words.length; i++) {
			//				System.out.print(words[i] + ":" + seq.get(i) + "  ");
			//			}
			//			System.out.println();
			if (!hasCoordinationBetweenVerbs(seq, words)) {
				return;
			}
			if (hasPredicate(seq) && hasCorrectCoordination(seq) && !doesAnyPredFollowPrep(seq)) {
				//				System.out.println("here");
//				System.out.println(seq);
				List<Pair<String, List<String>>> poss_pred_args = extractPredArgStructures(words, seq);
				if (poss_pred_args == null) {
					//					System.out.println("null args");
					return;
				}
				boolean okay = true;
//				System.out.println(poss_pred_args);
				for (int i = 0; i < poss_pred_args.size(); i++) {
					Pair<String, List<String>> pair = poss_pred_args.get(i);
					List<String> args = pair.getSecond();
					for (String arg : args) {
						String[] split = arg.split(",");
						if (split.length == 2 && arg.contains(" and ")) {
							// new after EMNLP 2015
							okay = false;
							break;
						} else if (arg.startsWith("and ")) {
							// new after EMNLP 2015
							okay = false;
							break;
						} else {
							boolean all_and = true;
							boolean an_and = false;
							for (int z = 1; z < split.length - 1; z++) {
								if (!split[z].startsWith("and ")) {
									all_and = false;
								} else {
									an_and = true;
								}
							}
							if (!all_and && an_and) {
								okay = false;
								break;
							}
						}
						if (!okay) {
							break;
						}
					}
					if (!okay) {
						break;
					}
				}
				for (int i = 0; i < poss_pred_args.size() - 1; i++) {
				// new after emnlp 2015
				//for (int i = 0; i < poss_pred_args.size(); i++) {
					Pair<String, List<String>> pair = poss_pred_args.get(i);
					List<String> args = pair.getSecond();
					for (String arg : args) {
						
						if (!doesArgHaveNoun(arg)) {
//														System.out.println(arg + " has no noun");
							okay = false;
							break;
						}
						String[] split = arg.split(",");
//						System.out.println(split.length);
//						System.out.println(split);
//						System.out.println(arg);
						if (split.length > 1 && !arg.contains(" and ")) {
							okay = false;
							//							System.out.println("not okay");
							break;
						} else if (split.length == 2 && arg.contains(" and ")) {
							// new after EMNLP 2015
							okay = false;
							break;
						} else if (arg.startsWith("and ")) {
							// new after EMNLP 2015
							okay = false;
							break;
						} else {
							boolean all_and = true;
							boolean an_and = false;
							for (int z = 1; z < split.length - 1; z++) {
								if (!split[z].startsWith("and ")) {
									all_and = false;
								} else {
									an_and = true;
								}
							}
							if (!all_and && an_and) {
								okay = false;
								break;
							}
						}
					}
					if (!okay) {
						break;
					}
				}
				if (okay) {
					seqs.add(seq);
				}
			}
			return;
		}
		String word = words[start];
		String tag = tags[start];
		boolean is_num = true;
		try {
			Integer.parseInt(tag);
		} catch (NumberFormatException ex) {
			is_num = false;
		}
		if (is_num) {
			if (start != 0) {
				String prev_tag = seq.get(seq.size() - 1);
				//				if (only_verbs.contains(word)) {
				//					seq.add(PREDICATE);
				//					getPossibleSequences(words, tags, start + 1, seq, seqs);
				//				} else {
				List<String> copy_seq = new ArrayList<String>(seq);
				seq.add("NN");
				getPossibleSequences(words, tags, start + 1, seq, seqs);
				copy_seq.add(PREDICATE);
				getPossibleSequences(words, tags, start + 1, copy_seq, seqs);
				//				}
			} else {
				//				if (word.toLowerCase().startsWith("leek")) {
				//					System.out.println(StringUtils.join(words));
				//					System.exit(1);
				//				}
				seq.add(PREDICATE);
				getPossibleSequences(words, tags, start + 1, seq, seqs);
			}
//		} else if (tag.equals("VBG")) {
//			List<String> copy_seq = new ArrayList<String>(seq);
//			seq.add("NN");
//			getPossibleSequences(words, tags, start + 1, seq, seqs);
//			copy_seq.add(tag);
//			getPossibleSequences(words, tags, start + 1, copy_seq, seqs);
		} else {
			seq.add(tag);
			getPossibleSequences(words, tags, start + 1, seq, seqs);
		}
	}

	
	public String[] getInitialTagsLC(String[] words) {
		String[] tags = new String[words.length];
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			String tag = getTag(word);
			//						System.out.println(word + ":" + tag);
			if (tag.equals("IN") || tag.equals("TO") || tag.equals("RB") || 
					tag.startsWith("PRP") || tag.startsWith("CC")
					|| tag.startsWith("-") || tag.startsWith("W") || tag.startsWith("VBG")) {
				if (word.equals("through")) {
					tags[i] = "RB";
				} else if (i != 0 && (tags[i - 1].matches("DT|CD|IN|TO") || tags[i - 1].startsWith("W"))) {
					tags[i] = "NN";
				} else {
					tags[i] = tag;
				}
//			} else if (tag.startsWith("VBG")) {
//				tags[i] = "NN";
			} else if (tag.matches("DT|CD")) {
				tags[i] = tag;
			} else if (word.equals("then")) {
				tags[i] = "CC";
			} else if ((word.matches("according") && words[i+1].equals("to"))
					|| (word.matches("because") && words[i+1].equals("of"))
					|| (word.matches("instead") && words[i+1].equals("of"))
					|| (word.matches("out") && words[i+1].equals("of"))) {
				tags[i] = "IN";
			} else if (word.matches("lrb|rrb|lsb|rsb")) {
				tags[i] = word;
			} else if (word.replaceAll("[^A-Za-z]","").equals("")) {
				tags[i] = tag;
			} else {
				Integer count = verb_counts.get(word);
				//				System.out.println(count);
				if (count == null || (i != 0 && (tags[i - 1].matches("DT|CD") || tags[i - 1].startsWith("W")))
						|| (i != 0 && tags[i - 1].equals("IN") && !words[i - 1].toLowerCase().equals("then"))) {
					tags[i] = "NN";
				} else {
					tags[i] = Integer.toString(count);
				}
			}
		}
		return tags;
	}
	
	public String[] getInitialTags(String[] words) {
		String[] tags = new String[words.length];
		for (int i = 0; i < words.length; i++) {
			String word = words[i].toLowerCase();
			String tag = getTag(word.toLowerCase());
			//						System.out.println(word + ":" + tag);
			if (tag.equals("IN") || tag.equals("TO") || tag.equals("RB") || 
					tag.startsWith("PRP") || tag.startsWith("CC")
					|| tag.startsWith("-") || tag.startsWith("W") || tag.startsWith("VBG")) {
				if (word.equals("through")) {
					tags[i] = "RB";
				} else if (i != 0 && (tags[i - 1].matches("DT|CD|IN|TO") || tags[i - 1].startsWith("W"))) {
					tags[i] = "NN";
				} else {
					tags[i] = tag;
				}
			} else if (tag.matches("DT|CD")) {
				tags[i] = tag;
			} else if (word.equals("then")) {
				tags[i] = "CC";
			} else if ((word.matches("according") && words[i+1].equals("to"))
					|| (word.matches("because") && words[i+1].equals("of"))
					|| (word.matches("instead") && words[i+1].equals("of"))
					|| (word.matches("out") && words[i+1].equals("of"))) {
				tags[i] = "IN";
			} else if (word.matches("lrb|rrb|lsb|rsb")) {
				tags[i] = word;
			} else if (word.replaceAll("[^A-Za-z]","").equals("")) {
				tags[i] = tag;
			} else {
				Integer count = verb_counts.get(word);
				//				System.out.println(count);
				if (count == null || (i != 0 && (tags[i - 1].matches("DT|CD") || tags[i - 1].startsWith("W")))
						|| (i != 0 && tags[i - 1].equals("IN") && !words[i - 1].toLowerCase().equals("then"))) {
					tags[i] = "NN";
				} else {
					tags[i] = Integer.toString(count);
				}
			}
		}
		return tags;
	}

	public boolean hasPredicate(String[] tags) {
		for (String tag : tags) {
			if (tag.equals(PREDICATE)) {
				return true;
			}
		}
		return false;
	}

	public boolean hasPredicate(List<String> tags) {
		for (String tag : tags) {
			if (tag.equals(PREDICATE)) {
				return true;
			}
		}
		return false;
	}

	public boolean shouldIgnoreSentence(String[] words, String[] tags) {
		String first_tag = tags[0];
		if (first_tag.equals("DT") || first_tag.startsWith("PRP") || 
				!Character.isLetterOrDigit(first_tag.charAt(0))) {
			return true;
		}
		int upper_case_count = 0;
		int verb_count = 0;
		boolean ends_in_colon = words[words.length - 1].equals(":");
		boolean has_calories = false;
		boolean has_other_nutrition = false;
		for (String word : words) {
			String lcw = word.toLowerCase();
			if (word.indexOf('@') != -1) {
				return true;
			}
			if (word.equals("I")) {
				return true;
			}
			if (lcw.matches("be|will|not|formatted|submitted|copyright|-rcb-|-lcb-")) {
				return true;
			}
			//new after emnlp
			if (lcw.matches("typed")) {
				return true;
			}
			// cal. new after emnlp
			if (lcw.matches("calories|cal\\.")) {
				has_calories = true;
				if (has_other_nutrition) {
					return true;
				}
			}
			if (lcw.matches("fat|carbohydrates|protein")) {
				has_other_nutrition = true;
				if (has_calories) {
					return true;
				}
			}
			if (ends_in_colon && Character.isUpperCase(word.charAt(0))) {
				upper_case_count++;
			}
		}
		if (upper_case_count > 3) {
			return true;
		}
		for (String tag : tags) {
			if (Measurements.isNumericString(tag)) {
				verb_count++;
			}
		}
		if (verb_count > 15) {
			return true;
		}
		return false;
	}
	
	public boolean shouldIgnoreSentenceLC(String[] words, String[] lowerwords, String[] tags) {
		String first_tag = tags[0];
		if (first_tag.equals("DT") || first_tag.startsWith("PRP") || 
				!Character.isLetterOrDigit(first_tag.charAt(0))) {
			return true;
		}
		
		int upper_case_count = 0;
		int verb_count = 0;
		boolean ends_in_colon = words[words.length - 1].equals(":");
		boolean has_other_nutrition = false;
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			String lcw = lowerwords[i];
			if (word.indexOf('@') != -1) {
				return true;
			}
			if (word.equals("I")) {
				return true;
			}
			if (lcw.matches("be|will|not|formatted|submitted|copyright|-rcb-|-lcb-")) {
				return true;
			}
			//new after emnlp
			if (lcw.matches("typed")) {
				return true;
			}
			// cal. new after emnlp 2015
			if (lcw.matches("fat|carbohydrates|protein|calories|cal\\.|carbohydrate|carbohydrates|sodium|fiber|potassium|carb|carbs")) {
				if (has_other_nutrition) {
					return true;
				}
				if (i != 0) {
					String prev = lowerwords[i-1];
					if (Measurements.isNumericString(prev)) {
						return true;
					}
					if (i > 1) {
						String prev2 = lowerwords[i-2];
						if (Measurements.isNumericString(prev2) && (prev.matches("mg|gram|grams|g|gm") || prev.endsWith("mg"))) {
							return true;
						}
					}
				}
				has_other_nutrition = true;
			}
			if (ends_in_colon && Character.isUpperCase(word.charAt(0))) {
				upper_case_count++;
			}
		}
		if (upper_case_count > 3) {
			return true;
		}
		for (String tag : tags) {
			if (Measurements.isNumericString(tag)) {
				verb_count++;
			}
		}
		if (verb_count > 15) {
			return true;
		}
		return false;
	}

	private boolean doesPredFollowCoord(List<String> tags, int next) {
		for (int i = next; i < tags.size(); i++) {
			String tag = tags.get(i);
			if (tag.matches(PREDICATE)) {
				return true;
			}
			if (tag.matches("NN")) {
				return false;
			}
		}
		return false;
	}

	private boolean doesAnyPredFollowPrep(List<String> tags) {
		for (int i = 1; i < tags.size(); i++) {
			String tag = tags.get(i);
			if (tag.equals(PREDICATE)) {
				String prev = tags.get(i-1);
				if (prev.matches("IN|TO") || prev.equals(PREDICATE)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean doesPredPrecedeCoord(List<String> tags, int prev) {
		for (int i = prev; i >= 0; i--) {
			String tag = tags.get(i);
			if (tag.matches(PREDICATE)) {
				return true;
			}
			if (tag.matches("NN|VBG")) {
				return false;
			}
		}
		return false;
	}
	//	
	private boolean doesCoordFollowList(List<String> tags, int prev) {
		int comma_cnt = 0;
		for (int i = prev; i >= 0; i--) {
			String tag = tags.get(i);
			//			System.out.println(i + ":" + tag);
			if (tag.equals(PREDICATE) || tag.matches("IN|RB|TO") || tag.startsWith("W")) {
				if (comma_cnt > 0) {
					//					System.out.println(prev + " " + comma_cnt);
					return false;
				}
				return true;
			}
			if (tag.equals("CC")) {
				//				System.out.println(prev + " " + comma_cnt);
				return true;
			}
			if (tag.equals(",")) {
				if (i != prev) {
					comma_cnt++;
				}
			}
		}
		if (comma_cnt > 0) {
			//			System.out.println(prev + " " + comma_cnt);
			return false;
		}
		//		System.out.println(prev + " " + comma_cnt);
		return true;
	}
	
	public boolean hasCoordinationBetweenVerbs(List<String> tags, String[] words) {
		boolean had_coord = true;
		boolean has_other_word = false;
		for (int i = 0; i < tags.size(); i++) {
			String tag = tags.get(i);
			if (tag.equals(PREDICATE)) {
				if (!had_coord && has_other_word) {
					List<String> best_tag = Utils.lemmatizer_.tag(words[i]);
					if (!best_tag.get(0).equals("VB")) {
						return false;
					}
				}
				// new post EMNLP 2015
				if ((i < tags.size() - 2) && (words[i+1].matches(",") && !(tags.get(i+2).equals(PREDICATE)
						&& !(tags.get(i+2).matches("IN|TO"))))) {
					return false;
				}
				had_coord = false;
				has_other_word = false;
			} else if (tag.equals("CC") || words[i].matches(":|;")) {
				had_coord = true;
			} else if (!words[i].matches(",")) {
				has_other_word = true;
			}
		}
		return true;
	}

	public boolean hasCorrectCoordination(List<String> tags) {
		for (int i = tags.size() - 1; i >= 0; i--) {
			String tag = tags.get(i);
			if (tag.equals("CC") && i != tags.size() - 1) {
				if (doesPredFollowCoord(tags, i + 1)) {
					if (!doesCoordFollowList(tags, i - 1)) {
						//						System.out.println("bad list " + i);
						return false;
					}

				} else {
					if (doesPredPrecedeCoord(tags, i - 1)) {
						//						System.out.println("bad preceed " + i);
						return false;
					}
				}
			}
		}
		return true;
	}

	public static Pair<String, Set<String>> getPredArgStructureOfNode(ActionNode node) {
		RecipeEvent event = node.event();
		Set<String> args = new HashSet<String>();
		Argument dobj = event.dobj();
		args.add(dobj.string());
		Iterator<Argument> arg_it = event.prepositionalArgIterator();
		while (arg_it.hasNext()) {
			Argument arg = arg_it.next();
			args.add(arg.preposition() + " " + arg.string());
		}
		arg_it = event.otherArgIterator();
		while (arg_it.hasNext()) {
			Argument arg = arg_it.next();
			args.add(arg.string());
		}
		Pair<String, Set<String>> pred_args = new Pair<String, Set<String>>(event.predicate(), args);
		return pred_args;
	}

	public Pair<List<String>, Double> getMostProbableSequenceAndProb(String[] words) {
		String[] tags = getInitialTags(words);
		if (shouldIgnoreSentence(words, tags)) {
			return null;
		}
		//		for (int i = 0; i < words.length; i++) {
		//			System.out.print(words[i] + ":" + tags[i]);
		//			System.out.print(" ");
		//		}
		//		System.out.println();
		//				System.out.println(StringUtils.join(tags));
		Set<List<String>> sequences = new HashSet<List<String>>();
		getPossibleSequences(words, tags, 0, new ArrayList<String>(), sequences);
		//		System.out.println(sequences.size());
		double max_prob = Double.NEGATIVE_INFINITY;
		List<String> best_seq = null;

		for (List<String> seq : sequences) {
			List<Pair<String, List<String>>> pred_args = extractPredArgStructures(words, seq);
			double lprob = logProbOfSequence(pred_args);
			//			System.out.println(pred_args + " " + lprob);
			if (lprob > max_prob) {
				max_prob = lprob;
				best_seq = seq;
			}
		}
		if (Double.isInfinite(max_prob)) {
			return new Pair<List<String>, Double>(best_seq, 0.0);
		}
		return new Pair<List<String>, Double>(best_seq, max_prob);
	}
	
	public Pair<List<String>, Double> getMostProbableSequenceAndProbLC(String[] words, String[] normalwords) {
		String[] tags = getInitialTagsLC(words);
		if (shouldIgnoreSentenceLC(normalwords, words, tags)) {
			return null;
		}
		//		for (int i = 0; i < words.length; i++) {
		//			System.out.print(words[i] + ":" + tags[i]);
		//			System.out.print(" ");
		//		}
		//		System.out.println();
		//				System.out.println(StringUtils.join(tags));
		Set<List<String>> sequences = new HashSet<List<String>>();
		getPossibleSequences(words, tags, 0, new ArrayList<String>(), sequences);
		//		System.out.println(sequences.size());
		double max_prob = Double.NEGATIVE_INFINITY;
		List<String> best_seq = null;

		for (List<String> seq : sequences) {
			List<Pair<String, List<String>>> pred_args = extractPredArgStructuresLC(words, seq);
			double lprob = logProbOfSequence(pred_args);
			//			System.out.println(pred_args + " " + lprob);
			if (lprob > max_prob) {
				max_prob = lprob;
				best_seq = seq;
			}
		}
		if (Double.isInfinite(max_prob)) {
			return new Pair<List<String>, Double>(best_seq, 0.0);
		}
		return new Pair<List<String>, Double>(best_seq, max_prob);
	}
	
	public List<String> getMostProbableSequence(String[] words) {
		//		System.out.println(StringUtils.join(words));
		String[] tags = getInitialTags(words);
		if (shouldIgnoreSentence(words, tags)) {
			return null;
		}
		//		for (int i = 0; i < words.length; i++) {
		//			System.out.print(words[i] + ":" + tags[i]);
		//			System.out.print(" ");
		//		}
		//		System.out.println();
		//				System.out.println(StringUtils.join(tags));
		Set<List<String>> sequences = new HashSet<List<String>>();
		getPossibleSequences(words, tags, 0, new ArrayList<String>(), sequences);
		//		System.out.println(sequences.size());
		double max_prob = Double.NEGATIVE_INFINITY;
		List<String> best_seq = null;

		for (List<String> seq : sequences) {
			List<Pair<String, List<String>>> pred_args = extractPredArgStructures(words, seq);
			double lprob = logProbOfSequence(pred_args);
			//			System.out.println(pred_args + " " + lprob);
			if (lprob > max_prob) {
				max_prob = lprob;
				best_seq = seq;
			}
		}
		return best_seq;
	}

	public double getMostProbableSequenceProb(String[] words) {
		//		System.out.println(StringUtils.join(words));
		String[] tags = getInitialTags(words);
		if (shouldIgnoreSentence(words, tags)) {
			return 0.0;
		}
		//		for (int i = 0; i < words.length; i++) {
		//			System.out.print(words[i] + ":" + tags[i]);
		//			System.out.print(" ");
		//		}
		//		System.out.println();
		//				System.out.println(StringUtils.join(tags));
		Set<List<String>> sequences = new HashSet<List<String>>();
		getPossibleSequences(words, tags, 0, new ArrayList<String>(), sequences);
		//		System.out.println(sequences.size());
		if (sequences.size() == 0) {
			return 0.0;
		}
		double max_prob = Double.NEGATIVE_INFINITY;

		for (List<String> seq : sequences) {
			List<Pair<String, List<String>>> pred_args = extractPredArgStructures(words, seq);
			double lprob = logProbOfSequence(pred_args);
			if (lprob > max_prob) {
				max_prob = lprob;
			}
		}
		if (Double.isInfinite(max_prob)) {
			return 0.0;
		}
		return max_prob;
	}
	
	public static String removeParentheticals(String str) {
		while (true) {
			int lrb = str.indexOf("-lrb-");
			if (lrb == -1) {
				lrb = str.indexOf("-LRB-");
			}
			if (lrb == -1) {
				int rrb = str.indexOf("-rrb-");
				if (rrb == -1) {
					rrb = str.indexOf("-RRB-");
				}
				if (rrb != -1) {
					if (rrb + 6 >= str.length()) {
						return "";
					}
					return str.substring(rrb + 6);
				}
				return str;
			}
			int rrb = str.indexOf("-rrb-");
			if (rrb == -1) {
				rrb = str.indexOf("-RRB-");
			}
			if (rrb == -1) {
				return str.substring(0, lrb);
			} else if (rrb < lrb) {
				str = str.substring(rrb + 6);
			} else {
				if (rrb + 6 >= str.length()) {
					return str.substring(0, lrb);
				}
				str = str.substring(0, lrb) + str.substring(rrb + 6);
			}
		}
	}

	public void tryOut() {
		try {
			System.out.println("Try out!");
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				System.out.print("Sentence: ");
				String sentence = br.readLine();
				if (sentence.equals("quit")) {
					return;
				}
				sentence = removeParentheticals(sentence);
				System.out.println(sentence);
				String[] tokens = sentence.split(" ");
				String[] tags = getInitialTags(tokens);
				System.out.println(shouldIgnoreSentence(tokens, tags));
				for (int i = 0; i < tokens.length; i++) {
					System.out.print(tokens[i] + ":" + tags[i]);
					System.out.print(" ");
				}
				System.out.println();
				Set<List<String>> sequences = new HashSet<List<String>>();
				getPossibleSequences(tokens, tags, 0, new ArrayList<String>(), sequences);
				for (List<String> seq : sequences) {
					for (int i = 0; i < tokens.length; i++) {
						System.out.print(tokens[i] + ":" + seq.get(i));
						System.out.print(" ");
					}
					List<Pair<String, List<String>>> pred_args = extractPredArgStructures(tokens, seq);
					System.out.println(logProbOfSequence(pred_args));
					for (Pair<String, List<String>> pair : pred_args) {
						String pred = pair.getFirst();
						List<String> args = pair.getSecond();
						System.out.println("PRED: " + pred);
						for (String arg : args) {
							System.out.println("   ARG: " + arg);
						}
					}
					System.out.println();
				}
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private static void writeActionDiagramToFile(ActionDiagram ad, BufferedWriter bw) throws IOException {
		int curr_sent = -1;
		for (int n = 0; n < ad.numNodes(); n++) {
			ActionNode node = ad.getNodeAtIndex(n);
			RecipeEvent event = node.event();
			int sent_id = event.sentenceIndex();
			if (sent_id != curr_sent) {
				if (curr_sent != -1) {
					bw.write("\n");
				}
				bw.write("SENTID: " + sent_id + "\n");
				bw.write("SENT: " + event.sentence_string() + "\n");
				curr_sent = sent_id;
			}
			bw.write("PREDID: " + event.predicateIndexInSentence() + "\n");
			bw.write("PRED: " + event.full_pred_string() + "\n");

			Argument dobj = event.dobj();
			if (dobj != null && !dobj.string().equals("")) {
				bw.write("  DOBJ: " + dobj.string() + "\n");
			}
			Iterator<Argument> prep_it = event.prepositionalArgIterator();
			while (prep_it.hasNext()) {
				Argument prep = prep_it.next();
				if (prep.string().equals("")) {
					continue;
				}
				bw.write("  PARG: " + prep.string() + "\n");
				bw.write("    PREP: " + prep.preposition() + "\n");
			}
			Iterator<Argument> other_it = event.otherArgIterator();
			while (other_it.hasNext()) {
				Argument other = other_it.next();
				bw.write("  OARG: " + other.string() + "\n");
			}
			bw.write("\n");
		}
	}

	public void chunkExtraTest() {
		try {
			String step_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + "ExtraTest" + "/" + "ExtraTest" + ProjectParameters.STEP_SUFFIX + "/";
			List<File> category_step_files = Utils.getInputFiles(step_directory_name, "txt");

			String fulltest_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + "ExtraTest" + "/" + "ExtraTest" + ProjectParameters.FULLTEXT_SUFFIX + "/";
			List<File> category_fulltext_files = Utils.getInputFiles(fulltest_directory_name, "txt");

			String output_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + "ExtraTest" + "/" + "ExtraTest" + ProjectParameters.CHUNKED_SUFFIX + "/";
			Utils.createOutputDirectoryIfNotExist(output_directory_name);

			for (int i = 0; i < category_step_files.size(); i++) {
				File step_file = category_step_files.get(i);
				File fulltext_file = category_fulltext_files.get(i);
				String output_filename = output_directory_name + step_file.getName();
				ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromPredArgMap(this, step_file, fulltext_file);
				BufferedWriter bw = new BufferedWriter(new FileWriter(output_filename));
				writeActionDiagramToFile(ad, bw);
				bw.close();
			}

		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public void createStepFiles(String dir) {
		File directory = new File(dir);
		File[] filelist = directory.listFiles();
		for (File file : filelist) {
			if (file.getName().endsWith(".txt")) {
				try {
					BufferedReader br = new BufferedReader(new FileReader(file));
					String line;
					List<String> steps = new ArrayList<String>();
					boolean in_steps = false;
					while ((line = br.readLine()) != null) {
						if (line.trim().length() == 0) {
							continue;
						} else if (line.trim().equals("Steps")) {
							in_steps = true;
						} else if (line.trim().equals("Ingredients")) {
							break;
						} else if (line.trim().startsWith("Data Parsed")) {
							break;
						} else if (in_steps) {
							steps.add(line.trim());
						}
					}
					br.close();

					BufferedWriter bw = new BufferedWriter(new FileWriter("/Users/chloe/Research/recipes/AllRecipesSteps/" + file.getName()));
					for (String step : steps) {
						List<String> sentences = Utils.splitStepSentences(step);
						for (String sentence : sentences) {
							bw.write(sentence + "\n");
						}
					}
					bw.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	

	public void chunkDataset() {
		try {
			for (String category : ProjectParameters.ALL_RECIPE_TYPES) {
				System.out.println(category);

				String step_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.STEP_SUFFIX + "/";
				List<File> category_step_files = Utils.getInputFiles(step_directory_name, "txt");

				String fulltest_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
				List<File> category_fulltext_files = Utils.getInputFiles(fulltest_directory_name, "txt");

				String output_directory_name = ProjectParameters.DEFAULT_DATA_DIRECTORY + category + "/" + category + ProjectParameters.CHUNKED_SUFFIX + "/";
				Utils.createOutputDirectoryIfNotExist(output_directory_name);

				for (int i = 0; i < category_step_files.size(); i++) {
					File step_file = category_step_files.get(i);
					File fulltext_file = category_fulltext_files.get(i);
					String output_filename = output_directory_name + step_file.getName();
					ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromPredArgMap(this, step_file, fulltext_file);
					BufferedWriter bw = new BufferedWriter(new FileWriter(output_filename));
					writeActionDiagramToFile(ad, bw);
					bw.close();
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

}
