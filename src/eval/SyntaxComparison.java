package eval;

import graphics.BratAnnotationReader;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import data.ActionDiagram;
import data.RecipeSentenceSegmenter;
import data.ProjectParameters;
import data.ActionDiagram.ActionNode;
import data.RecipeEvent.Argument;
import data.RecipeEvent;
import utils.Utils;

public class SyntaxComparison {
	private RecipeSentenceSegmenter chunker_;

	public SyntaxComparison(String file) {
		try {
			chunker_ = RecipeSentenceSegmenter.readInFromFile(file);
		} catch (IOException ex) {
			ex.printStackTrace();
			System.exit(1);
		}
	}

	public void run(List<File[]> files) {
		int tp_pred = 0;
		int fp_pred = 0;
		int fn_pred = 0;
		Map<String, Integer> fp_preds = new HashMap<String, Integer>();
		Map<String, Integer> fn_preds = new HashMap<String, Integer>();

		int tp_args = 0;
		int fp_args = 0;
		int fn_args = 0;

		int found_conn_args = 0;
		int conn_args = 0;

		for (File[] instance : files) {
			File ann_file = instance[0];
			File arg_file = instance[1];
			File step_file = instance[2];
			File fulltext_file = instance[3];
//						ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromFile(arg_file, fulltext_file, false, true);
			ActionDiagram ad = ActionDiagram.generateNaiveActionDiagramFromPredArgMap(chunker_, step_file, fulltext_file);
			ActionDiagram gold = BratAnnotationReader.generativeActionDiagramFromBratAnnotations(ann_file, step_file, fulltext_file, true, false);
			Map<ActionNode, ActionNode> action_to_gold_map = GoldStandardEvaluator.actionToGoldSearch(ad, gold);
			Map<ActionNode, ActionNode> gold_to_action_map = new HashMap<ActionNode, ActionNode>();
			for (Map.Entry<ActionNode, ActionNode> entry : action_to_gold_map.entrySet()) {
				gold_to_action_map.put(entry.getValue(), entry.getKey());
			}
			Iterator<ActionNode> node_it = ad.node_iterator();
			while (node_it.hasNext()) {
				ActionNode node = node_it.next();
				RecipeEvent event = node.event();
				if (action_to_gold_map.get(node) == null) {
					Utils.incrementStringMapCount(fp_preds, event.predicate());
					//					if (!event.predicate().matches("clean|butter|sauce|blend|shape|grill|salt|smooth|cake|brown")) {
					//						System.out.println(step_file.getName());
					//						System.out.println(action_to_gold_map);
					//						System.out.println(event);
					//						System.exit(1);
					//					}
					fp_pred++;
					if (event.dobj() != null) {
						fp_args++;
					}
					Iterator<Argument> prep_it = event.prepositionalArgIterator();
					while (prep_it.hasNext()) {
						prep_it.next();
						fp_args++;
					}
				} else {
					ActionNode gold_node = action_to_gold_map.get(node);
					RecipeEvent gold_event = gold_node.event();
					Argument dobj = event.dobj();
					Argument gold_dobj = gold_event.dobj();
					//System.out.println(node.event());
					//System.out.println(gold_node.event());
					String dobj_str = null;
					if (dobj != null) {
						dobj_str = dobj.string().replaceAll(" and ", " ");
						dobj_str = dobj_str.replaceAll("-lrb-", "(").replaceAll("-rrb-", ")").replaceAll("[^A-Za-z0-9]","");
					}
					String gold_dobj_str = null;
					if (gold_dobj != null) {
						gold_dobj_str = gold_dobj.string();
						gold_dobj_str = gold_dobj_str.replaceAll("-lrb-", "(").replaceAll("-rrb-", ")").replaceAll("[^A-Za-z0-9]","");
					}
					if ((dobj == null || dobj.string().equals("")) && (gold_dobj != null && !gold_dobj.string().equals(""))) {
						fn_args++;
						if (gold_dobj_str != null && !gold_dobj_str.equals("")) {
							List<String> spans = gold_dobj.nonIngredientSpans();
							for (String span : spans) {
								if (gold_node.getOrigin(gold_dobj, span) != null) {
									conn_args++;
								}
							}
						}
					} else if ((dobj != null && !dobj.string().equals("")) && (gold_dobj == null || gold_dobj_str.equals(""))) {
						fp_args++;
					} else if (dobj != null && gold_dobj != null) {
						if (dobj_str.equals(gold_dobj_str)) {
							if (dobj_str.equals("")) {

							} else {
								tp_args++;
								List<String> spans = gold_dobj.nonIngredientSpans();
								for (String span : spans) {
									if (gold_node.getOrigin(gold_dobj, span) != null) {
										conn_args++;
										found_conn_args++;
									}
								}
							}
						} else {
							fp_args++;
							fn_args++;
							if (!gold_dobj.string().equals("")) {
								List<String> spans = gold_dobj.nonIngredientSpans();
								for (String span : spans) {
									if (gold_node.getOrigin(gold_dobj, span) != null) {
										conn_args++;
										if (dobj_str.contains(span)) {
											found_conn_args++;
										}
									}
								}
							}
						}
					}

					Iterator<Argument> prep_it = event.prepositionalArgIterator();
					Iterator<Argument> gold_prep_it = gold_event.prepositionalArgIterator();
					Map<String, Argument> prep_strings = new TreeMap<String, Argument>();
					while (prep_it.hasNext()) {
						Argument prep = prep_it.next();
						prep_strings.put(prep.preposition() + prep.string().replaceAll("-lrb-", "(").replaceAll("-rrb-", ")").replaceAll("[^A-Za-z0-9]",""), prep);
					}
					Map<String, Argument> gold_prep_strings = new TreeMap<String, Argument>();
					while (gold_prep_it.hasNext()) {
						Argument prep = gold_prep_it.next();
						if (prep.string().replaceAll("[^A-Za-z0-9]","").equals("")) {
							continue;
						}
						if (gold_prep_strings.containsKey(prep.preposition() + prep.string().replaceAll("[^A-Za-z0-9]",""))) {
							System.out.println(gold_event);
							System.exit(1);
						}
						gold_prep_strings.put(prep.preposition() + prep.string().replaceAll("[^A-Za-z0-9]",""), prep);
					}
					prep_it = event.otherArgIterator();
					gold_prep_it = gold_event.otherArgIterator();
					while (prep_it.hasNext()) {
						Argument prep = prep_it.next();
						prep_strings.put(prep.string().replaceAll("-lrb-", "(").replaceAll("-rrb-", ")").replaceAll("[^A-Za-z0-9]",""), prep);
					}
					while (gold_prep_it.hasNext()) {
						Argument prep = gold_prep_it.next();
						if (prep.string().replaceAll("[^A-Za-z0-9]","").equals("")) {
							continue;
						}
						if (gold_prep_strings.containsKey(prep.string().replaceAll("[^A-Za-z0-9]",""))) {
							System.out.println(gold_event);
							System.exit(1);
						}
						gold_prep_strings.put(prep.string().replaceAll("[^A-Za-z0-9]",""), prep);
					}
					if (prep_strings.size() == 0 && gold_prep_strings.size() != 0) {
						fn_args += gold_prep_strings.size();
						for (String gold_prep_string : gold_prep_strings.keySet()) {
							Argument gold_prep = gold_prep_strings.get(gold_prep_string);
							if (gold_prep.string().equals("")) {
								continue;
							}
							List<String> spans = gold_prep.nonIngredientSpans();
							for (String span : spans) {
								if (gold_node.getOrigin(gold_prep, span) != null) {
									conn_args++;
								}
							}
						}
					} else if (prep_strings.size() != 0 && gold_prep_strings.size() == 0) {
						fp_args += prep_strings.size();
					} else if (prep_strings.size() != 0 && gold_prep_strings.size() != 0) {
						Iterator<String> it = prep_strings.keySet().iterator();
						Iterator<String> gold_it = gold_prep_strings.keySet().iterator();
						String str = it.next();
						String gold_str = gold_it.next();
						//System.out.println("PREPS");
						//System.out.println(prep_strings);
						//System.out.println(gold_prep_strings);
						while (true) {
							//								System.out.println(str + " " + gold_str);
							if (str.equals(gold_str)) {
								tp_args++;

								Argument gold_prep = gold_prep_strings.get(gold_str);
								List<String> spans = gold_prep.nonIngredientSpans();
								for (String span : spans) {
									if (gold_node.getOrigin(gold_prep, span) != null) {
										conn_args++;
										found_conn_args++;
									}
								}

								if (it.hasNext()) {
									str = it.next();
								} else {
									while (gold_it.hasNext()) {
										gold_str =  gold_it.next();
										gold_prep = gold_prep_strings.get(gold_str);
										spans = gold_prep.nonIngredientSpans();
										for (String span : spans) {
											if (gold_node.getOrigin(gold_prep, span) != null) {
												conn_args++;
												for (String s : prep_strings.keySet()) {
													Argument arg = prep_strings.get(s);
													if (arg.string().contains(span)) {
														found_conn_args++;
														break;
													}
												}
											}
										}
										//											System.out.println(gold_str);
										fn_args++;
									}
									break;
								}
								if (gold_it.hasNext()) {
									gold_str = gold_it.next();
								} else {
									while (it.hasNext()) {
										str = it.next();
										//											System.out.println(str);
										fp_args++;
									}
									break;
								}
							} else if (str.compareTo(gold_str) < 0) {
								fp_args++;
								if (it.hasNext()) {
									str = it.next();
								} else {
									Argument gold_prep = gold_prep_strings.get(gold_str);
									List<String> spans = gold_prep.nonIngredientSpans();
									for (String span : spans) {
										if (gold_node.getOrigin(gold_prep, span) != null) {
											conn_args++;
											for (String s : prep_strings.keySet()) {
												Argument arg = prep_strings.get(s);
												if (arg.string().contains(span)) {
													found_conn_args++;
													break;
												}
											}
										}
									}
									//											System.out.println(gold_str);
									fn_args++;
									while (gold_it.hasNext()) {
										gold_str =  gold_it.next();
										gold_prep = gold_prep_strings.get(gold_str);
										spans = gold_prep.nonIngredientSpans();
										for (String span : spans) {
											if (gold_node.getOrigin(gold_prep, span) != null) {
												conn_args++;
												for (String s : prep_strings.keySet()) {
													Argument arg = prep_strings.get(s);
													if (arg.string().contains(span)) {
														found_conn_args++;
														break;
													}
												}
											}
										}
										//											System.out.println(gold_str);
										fn_args++;
									}
									break;
								}
							} else {
								fn_args++;
								Argument gold_prep = gold_prep_strings.get(gold_str);
								List<String> spans = gold_prep.nonIngredientSpans();
								for (String span : spans) {
									if (gold_node.getOrigin(gold_prep, span) != null) {
										conn_args++;
										for (String s : prep_strings.keySet()) {
											Argument arg = prep_strings.get(s);
											if (arg.string().contains(span)) {
												found_conn_args++;
												break;
											}
										}
									}
								}

								if (gold_it.hasNext()) {
									gold_str = gold_it.next();
								} else {
									while (it.hasNext()) {
										str = it.next();
										//											System.out.println(str);
										fp_args++;
									}
									break;
								}
							}
						}

					}
					tp_pred++;
				}
			}
			System.out.println("here");
			node_it = gold.node_iterator();
			while (node_it.hasNext()) {
				ActionNode node = node_it.next();
				RecipeEvent event = node.event();
				if (gold_to_action_map.get(node) == null) {
					Utils.incrementStringMapCount(fn_preds, event.predicate());
					fn_pred++;
					if (event.predicate().matches("finish")) {
						System.out.println(step_file.getName());
						System.out.println(action_to_gold_map);
						System.out.println(event);
						System.exit(1);
					}
					if (event.dobj() != null && !event.dobj().string().replaceAll("[^A-Za-z0-9]","").equals("")) {
						fn_args++;

						List<String> spans = event.dobj().nonIngredientSpans();
						for (String span : spans) {
							if (node.getOrigin(event.dobj(), span) != null) {
								conn_args++;
							}
						}
					}
					Iterator<Argument> prep_it = event.prepositionalArgIterator();
					while (prep_it.hasNext()) {
						Argument prep = prep_it.next();
						if (prep.string().replaceAll("[^A-Za-z0-9]","").equals("")) {
							continue;
						}
						List<String> spans = prep.nonIngredientSpans();
						for (String span : spans) {
							if (node.getOrigin(prep, span) != null) {
								conn_args++;
							}
						}
						fn_args++;
					}
					prep_it = event.otherArgIterator();
					while (prep_it.hasNext()) {
						Argument prep = prep_it.next();
						if (prep.string().replaceAll("[^A-Za-z0-9]","").equals("")) {
							continue;
						}
						List<String> spans = prep.nonIngredientSpans();
						for (String span : spans) {
							if (node.getOrigin(prep, span) != null) {
								conn_args++;
							}
						}
						fn_args++;
					}
				}
			}
		}
		System.out.println("True pos: " + tp_pred);
		System.out.println("False pos: " + fp_pred);
		System.out.println("False pos preds: " + fp_preds);
		System.out.println("False neg: " + fn_pred);
		System.out.println("False neg preds: " + fn_preds);

		System.out.println("True pos args: " + tp_args);
		System.out.println("False pos args: " + fp_args);
		System.out.println("False neg args: " + fn_args);

		System.out.println("Found conns: " + found_conn_args);
		System.out.println("Total conns: " + conn_args);
	}

	public static List<File[]> getDevFileList() {
		List<File[]> arg_and_fulltext_files = new ArrayList<File[]>();

		for (String category : ProjectParameters.ALL_RECIPE_TYPES) {
			System.out.println(category);
			String step_directory_name = "/home/chloe/Data/allrecipes/" + category + "/" + category + ProjectParameters.STEP_SUFFIX + "/";
			List<File> step_files = Utils.getInputFiles(step_directory_name, "txt");

			String arg_directory_name = "/home/chloe/Data/allrecipes/" + category + "/" + category + ProjectParameters.CHUNKED_SUFFIX + "/";
			List<File> arg_files = Utils.getInputFiles(arg_directory_name, "txt");


			String fulltext_directory_name = "/home/chloe/Data/allrecipes/" + category + "/" + category + ProjectParameters.FULLTEXT_SUFFIX + "/";
			List<File> fulltext_files = Utils.getInputFiles(fulltext_directory_name, "txt");

			String ann_directory_name = "/home/chloe/Data/allrecipes/" + "AnnotationSession" + "/" + "AnnotationSession" + "-splitann/" + category + "/";
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
	
	public static void main(String[] argv) {
		/*
		 * files[0] = ann_file;
			files[1] = arg_file;
			files[2] = step_file;
			files[3] = fulltext_file;
		 */
		List<File[]> dev_files = getDevFileList();
		SyntaxComparison comparer = new SyntaxComparison(argv[0]);
		comparer.run(dev_files);
	}
}
