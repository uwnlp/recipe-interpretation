package utils;

import java.io.IOException;


public class Dot {

  public static void getSVG(String dotFile, String svgFile) throws IOException, InterruptedException {
    
    Runtime rt = Runtime.getRuntime();
    Process pr = rt.exec("dot -Tsvg " + dotFile + " -o " + svgFile);
    pr.waitFor();
    System.out.println("Dot returned" + pr.exitValue());
    
  }
}
