package dfism.commons;

/**
 * This class is used to record the maximum memory used by an algorithm during
 * a given execution.
 * It is implemented by using the "singleton" design pattern.
 */
public class MemoryLogger {
	
	// the only instance  of this class (this is the "singleton" design pattern)
	private static MemoryLogger instance = new MemoryLogger();
	private static Runtime runtime = Runtime.getRuntime();

	// variable to store the maximum memory usage
	private double maxUsedMemory = 0;
	
	/**
	 * Method to obtain the only instance of this class
	 * @return instance of MemoryLogger
	 */
	public static MemoryLogger getInstance(){
		return instance;
	}
	
	/**
	 * To get the maximum amount of memory (MB) used until now
	 * @return a double value indicating memory as megabytes
	 */
	public double getMaxUsedMemory() {
		return maxUsedMemory;
	}

	/**
	 * Reset the maximum amount of memory recorded.
	 */
	public void reset(){
		maxUsedMemory = 0;
	}
	
	/**
	 * Check the current memory usage and record it if it is higher
	 * than the amount of memory previously recorded.
	 */
	public void checkMemory() {
		double currentMemory = (runtime.totalMemory() -  runtime.freeMemory()) / 1048576;
		if (currentMemory > maxUsedMemory) maxUsedMemory = currentMemory;
	}
	
	/**
	 * Return available memory in MB at the current time
	 */
	public long getAvailableMemory(){
		return (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory())/1048576;
	}
}
