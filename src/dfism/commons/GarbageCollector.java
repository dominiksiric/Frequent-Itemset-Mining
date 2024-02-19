package dfism.commons;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;

public class GarbageCollector {
	
	 public static long getGarbageCollectionTime() {
	    long collectionTime = 0;
	    for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
	        collectionTime += garbageCollectorMXBean.getCollectionTime();
	    }
	    return collectionTime;
	}
	 
	 /**
	  * Collect memory
	  * @return running time
	  */
	 public static long collectMemory(){
		 long start = System.currentTimeMillis();
	     System.gc();
	     return System.currentTimeMillis() - start;
	 }
}
