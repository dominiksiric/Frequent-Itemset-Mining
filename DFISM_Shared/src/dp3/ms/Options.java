package dp3.ms;

class Options {
	public static String filter_filename = "";
	public static double support_threshold = -1;
	public static int part_count = 1;
  
	public static boolean parse(String args[]) {
		if (args.length % 2 != 0 && args.length < 4) {
			printHelp();
			return false;
		}
		
		try{
			for (int i = 0; i < args.length; i += 2) {
				if (args[i].equals("-ff") || args[i].equals("--filter_filename")){
					filter_filename = args[i + 1];
				} else if (args[i].equals("-st") || args[i].equals("--support_threshold")) {
					support_threshold = Double.parseDouble(args[i + 1]);
				} else if (args[i].equals("-pc") || args[i].equals("--part_count")) {
					part_count = Integer.parseInt(args[i + 1]);
				} else if (args[i].equals("-h") || args[i].equals("--help")) {
					printHelp();
				}
			}
		}catch(NumberFormatException e){
			return false;
		}
		
		return checkParameters();
	}
  
	private static boolean checkParameters() {
		return !filter_filename.equals("") && (support_threshold > 0) && (support_threshold <= 1) && (part_count >= 1);
	}
  
	public static String getString() {
		return "Mining parameters: [filter_filename=" + filter_filename + 
				", support_threshold=" + support_threshold + 
				", part_count=" + part_count + "]";
	}
  
	public static void printHelp() {
	    System.out.println("Options:");
	    
	    System.out.println("\t--filter_filename (-ff) Filter file name, is REQUIRED");
	    System.out.println("\t\tIf Config.is_distributed_file_system = true. "
	    		+ "The filter_filename is used to filter the input files in the input directory.");
	    System.out.println("\t\tIf Config.is_distributed_file_system = false. "
	    		+ "The filter_filename is used as the file name in the input directory at each slave host.");
	    
	    System.out.println("\t--support_threshold (-st) Support threshold, double >0 & <=1, is REQUIRED");
	    System.out.println("\t\tMinimum support of frequent itemsets found");
	    
	    System.out.println("\t--part_count (-pc) Part count, Integer >= 1, is OPTIONAL");
	    System.out.println("\t\tThe number of parts of global potential 2-itemsets");
	    
	    System.out.println("\t--help (-h)");
	    System.out.println("\t\tPrint out help");
	    
	    System.out.println("Example: -ff testing -st 0.1 -pc 1");
	    System.out.println();
	}
}