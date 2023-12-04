package dfism.threads;

import java.util.ArrayList;
import java.util.LinkedList;

public class LocalFrequentItemsMergingThread extends Thread{
	private ArrayList<ArrayList<String>> local_frequent_item_arrays;
	private LinkedList<Integer> jobQueue;
	private int id;
	
	public LocalFrequentItemsMergingThread(ArrayList<ArrayList<String>> local_frequent_item_arrays, LinkedList<Integer> jobQueue, int id){
		this.local_frequent_item_arrays = local_frequent_item_arrays;
		this.jobQueue = jobQueue;
		this.id = id;
		this.setPriority(Thread.MAX_PRIORITY);
	}
	
	// Overwrite the run method
	public void run(){
		int mergingId, mergedId, count=0;
		ArrayList<String> result_list;
		
		synchronized(jobQueue){
			if(jobQueue.size() < 2) return;
			mergingId = jobQueue.removeFirst();
			mergedId = jobQueue.removeFirst();
		}

		while(true){
			result_list = merge(local_frequent_item_arrays.get(mergingId), local_frequent_item_arrays.get(mergedId));
			local_frequent_item_arrays.set(mergingId, result_list);
			
        	count++;
        	
        	synchronized (jobQueue){
        		jobQueue.addLast(mergingId);
        		if(jobQueue.size() < 2) break;
        		mergingId = jobQueue.removeFirst();
    			mergedId = jobQueue.removeFirst();
        	}
		}
		
		// Just for testing
		StringBuilder sb = new StringBuilder();
		sb.append(this.getClass().getSimpleName()).append(' ').append(id).append(" processed ")
		.append(count).append(" units");
		System.out.println(sb.toString());
	}
	
	private ArrayList<String> merge(ArrayList<String> list1, ArrayList<String> list2){
		int count1 = list1.size();
		int count2 = list2.size();
		ArrayList<String> result_list = new ArrayList<String>(count1 > count2? count1 : count2);
		int i=0, j=0, compare;
		String item1, item2;
		
		while((i < count1) && (j < count2)){
			item1 = list1.get(i);
			item2 = list2.get(j);
			compare = item1.compareTo(item2);
			if(compare==0){
				result_list.add(item1);
				i++; j++;
			}else if(compare < 0){
				result_list.add(item1);
				i++;
			}else{
				result_list.add(item2);
				j++;
			}
		}
		
		for(; i<count1; i++) result_list.add(list1.get(i));
		for(; j<count2; j++) result_list.add(list2.get(j));
		
		return result_list;
	}
}
