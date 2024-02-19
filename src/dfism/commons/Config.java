package dfism.commons;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Config {
	private static final String default_config_filename = "./config";
	
	public enum MiningModes {FROM_SCRATCH, LOADTREE_BUILDUP, LOADTREE, BUILDUP, OTHER_SUPPORT, EXIT};
	public enum ReturnCodes {ERROR, SUCCESS, FINISH, CONTINUE};
	
	/**
	 * Slaves and master uses a common distributed file system or their own local hard disks
	 */
	public static boolean is_distributed_file_system = true;
	
	/**
	 * Auto mode = true : Parameters are provided for master from a file
	 * </br> Auto mode = false (Interactive mode): Parameters provides for master by command line
	 */
	public static boolean is_auto_mode = true;
	
	/**
	 * The port for a slave process running on a node, default value is 9000
	 */
	public static int slave_port = 9000;
	
	/**
	 * Directory of input data files
	 */
	public static String input_data_directory = "./input_data/";
	
	/**
	 * Directory of output data files
	 */
	public static String output_data_directory = "./output_data/";
	
	/**
	 * Incremental output directory of incremental algorithms
	 */
	public static String incremental_directory = "./inc_data/";
	
	/**
	 * If is_distributed_file_system = true, slaves will store their IP:Port into this directory 
	 */
	public static String slave_address_directory = "./slave_address/";
	
	/**
	 * If is_distributed_file_system = false, IP:Port of slaves must be configured in advance in this file
	 */
	public static String slave_address_file = "./slave_address_file";
	
	/**
	 * If auto mode = true, parameters for the master must be provided in this file.
	 */
	public static String difin_parameters_file = "./difin_parameters";
	
	public static enum PARAMETERS {
		IS_DISTRIBUTED_FILE_SYSTEM, 
		IS_AUTO_MODE,
		SLAVE_PORT,
		INPUT_DATA_DIRECTORY, 
		OUTPUT_DATA_DIRECTORY, 
		INCREMENTAL_DIRECTORY, 
		SLAVE_ADDRESS_DIRECTORY,
		SLAVE_ADDRESS_FILE,
		DIFIN_PARAMETERS_FILE
	};
	
	/**
	 * Parse the configuration file.
	 * </br> key=value for each line
	 * </br> '#' begin a line for a comment
	 * @param filename
	 * @throws IOException 
	 */
	public static void parse(String filename) throws IOException{
		BufferedReader input = new BufferedReader(new FileReader(filename));
		String line, param_name;
		String[] array;
		PARAMETERS param;
		while((line = input.readLine()) != null) {
			array = line.split("=");
			if(array.length!=2) continue;	// No value provided, or wrong syntax
			
			param_name = array[0].trim().toUpperCase();
			if(param_name.charAt(0) == '#') continue;	// Is a comment
			
			try{
				param = PARAMETERS.valueOf(param_name);
			}catch(IllegalArgumentException e){
				continue;	// Wrong parameter name
			}
			
			Config.updateValue(param, array[1].trim());
		}
		input.close();
	}
	
	/**
	 * Parse configuration file with default filename = "./config"
	 * @throws IOException 
	 */
	public static void parse() throws IOException{
		Config.parse(default_config_filename);
	}
	
	private static void updateValue(PARAMETERS param, String value){
		try{
			switch(param){
			case IS_DISTRIBUTED_FILE_SYSTEM:
				Config.is_distributed_file_system = Boolean.parseBoolean(value);
				break;
			case IS_AUTO_MODE:
				Config.is_auto_mode = Boolean.parseBoolean(value);
				break;
			case SLAVE_PORT:
				Config.slave_port = Integer.parseInt(value);
			case INPUT_DATA_DIRECTORY:
				Config.input_data_directory = value;
				break;
			case OUTPUT_DATA_DIRECTORY: 
				Config.output_data_directory = value;
				break;
			case INCREMENTAL_DIRECTORY: 
				Config.incremental_directory = value;
				break;
			case SLAVE_ADDRESS_DIRECTORY:
				Config.slave_address_directory = value;
				break;
			case SLAVE_ADDRESS_FILE:
				Config.slave_address_file = value;
				break;
			case DIFIN_PARAMETERS_FILE:
				Config.difin_parameters_file = value;
				break;
			}
		}catch(Exception e){
			
		}
	}
}
