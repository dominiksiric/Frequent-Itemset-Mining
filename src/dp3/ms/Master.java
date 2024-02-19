package dp3.ms;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import dfism.commons.Config;
import dfism.commons.Config.MiningModes;
import dfism.commons.GarbageCollector;
import dfism.commons.ItemSupport;
import dfism.commons.MemoryLogger;
import dfism.commons.SlaveInfo;
import dfism.fpo.FPOTree;
import dfism.threads.GlobalFrequentItemsSendingThread;
import dfism.threads.GlobalPotentialItemsSendingThread;
import dfism.threads.Local2ItemsetsFPOTreeReceivingThread;
import dfism.threads.LocalFPOTreeReceivingThread;
import dfism.threads.LocalFrequentItemsMergingThread;
import dfism.threads.LocalFrequentItemsReceivingThread;
import dfism.threads.LocalSupportCountOfPotentialItemsReceivingThread;
import dfism.threads.ParameterSendingThread;
import dfism.threads.SupportArrayReceivingThread;
import dp3.p3e.PrePostPlusE;
import dp3.p3e.FrequentItemsAndNLists;
import dfism.commons.GarbageCollector;
import dfism.commons.IntHolder;
import dfism.commons.Matrix;
import dfism.commons.MemoryLogger;
import dfism.fpo.FISMAlgorithm;
import dfism.fpo.FPSubset;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.io.*;
import java.net.*;
import java.util.*;
import java.io.DataInputStream;



// Note: Most parts of this algorithm follow a similair structure as the DP3 algorithm, this is for
// making imports more convinient. Parts are reused from DP3 in both the Master and Slave.
public class Master {
    private List<Slave> slaves = new ArrayList<>();
    private List<String> frequentItems;
    private List<List<String>> nListsOf2Itemsets;
    private List<String> frequent2Itemsets = new ArrayList<>();
    private List<Integer> taskStatus = new ArrayList<>();
    private int totalFrequentItemsets = 0;

    public static final int TASK_REQUEST = 1;
    public static final int NO_MORE_TASK = 2;
    public static final int NUM_FREQ_ITEMSETS = 3;

    public void addSlave(Slave slave) {
        slaves.add(slave);
        taskStatus.add(0); // Initialize task status for each slave
    }

    private List<String> buildPPCTreeAndFindFrequentItems(String dataDirectory) {
        List<String> frequentItems = new ArrayList<>();

        // Build PPC-tree and find frequent items
        try {
            // Iterate through files in the data directory
            File directory = new File(dataDirectory);
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String dataFilePath = file.getAbsolutePath();
                        double threshold = 0.1; // Example threshold, replace with your desired value
                        PrePostPlusE alg = new PrePostPlusE(dataFilePath, threshold);

                        // Prepare PPC-tree
                        alg.prepareToConstructPPCTree(frequentItems); // Pass frequentItems to initialize item_globalIndex

                        // Construct PPC-tree
                        alg.constructPPCTree();

                        // Mine frequent itemsets
                        alg.mining();

                        frequentItems.addAll(alg.getFrequentItemsInItemNameBasedOrder());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return frequentItems;
    }

    private List<List<String>> generateNListsOf2Itemsets(List<String> frequentItems) {
        List<List<String>> nLists = new ArrayList<>();

        // Generate N-lists of 2-itemsets
        for (String item1 : frequentItems) {
            List<String> nList = new ArrayList<>();
            for (String item2 : frequentItems) {
                if (!item1.equals(item2)) {
                    nList.add(item1 + " " + item2);
                }
            }
            nLists.add(nList);
        }

        return nLists;
    }

    public void sendFrequentItemsAnd2ItemsetsToSlaves() {
        for (Slave slave : slaves) {
            try {
                DataOutputStream dos = slave.getDataOutputStream();
                // Send the number of frequent items
                dos.writeInt(frequentItems.size());
                // Send frequent items
                for (String item : frequentItems) {
                    dos.writeUTF(item);
                }
                // Send the number of N-lists of 2-itemsets
                dos.writeInt(nListsOf2Itemsets.size());
                // Send N-lists of 2-itemsets
                for (List<String> nList : nListsOf2Itemsets) {
                    // Send the size of the list
                    dos.writeInt(nList.size());
                    // Send each item in the list
                    for (String item : nList) {
                        dos.writeUTF(item);
                    }
                }
                dos.flush();
            } catch (IOException e) {
                e.printStackTrace();
                // Handle the exception as needed
            }
        }
    }

    public void distributeTasks() {
        int numTasks = frequent2Itemsets.size();
        for (int i = 0; i < slaves.size(); i++) {
            Slave slave = slaves.get(i);
            if (taskStatus.get(i) < numTasks) {
                try {
                    DataOutputStream dos = slave.getDataOutputStream();
                    dos.writeInt(TASK_REQUEST);
                    dos.writeUTF(frequent2Itemsets.get(taskStatus.get(i)));
                    dos.flush();
                    taskStatus.set(i, taskStatus.get(i) + 1);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void handleTaskCompletion(Slave slave, int numItemsets) {
        totalFrequentItemsets += numItemsets;
        if (totalFrequentItemsets >= frequent2Itemsets.size()) {
            // All tasks completed, inform slaves
            for (Slave s : slaves) {
                try {
                    DataOutputStream dos = s.getDataOutputStream();
                    dos.writeInt(NO_MORE_TASK);
                    dos.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void receiveFrequentItemsetsFromSlaves() {
        for (Slave slave : slaves) {
            try {
                DataInputStream dis = slave.getDataInputStream();
                int numItemsets = dis.readInt();
                for (int i = 0; i < numItemsets; i++) {
                    String frequentItemset = dis.readUTF();
                    // Process the received frequent itemset as needed
                    System.out.println("Received frequent itemset from slave: " + frequentItemset);
                }
                handleTaskCompletion(slave, numItemsets); // Update task completion status
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        try {
            // Load configurations from the Config file
            Config.parse();

            // Get configurations from the Config file
            String inputDataDirectory = Config.input_data_directory;

            Master master = new Master();

            // Step 1: Initialize frequent items and prepare PPC-tree
            System.out.println("Initializing frequent items and preparing PPC-tree...");
            master.frequentItems = master.buildPPCTreeAndFindFrequentItems(inputDataDirectory);
            System.out.println("Frequent Items:");
            for (String item : master.frequentItems) {
                System.out.println(item);
            }

            // Step 2: Generate N-lists of 2-itemsets
            System.out.println("Generating N-lists of 2-itemsets...");
            master.nListsOf2Itemsets = master.generateNListsOf2Itemsets(master.frequentItems);
            for (int i = 0; i < master.nListsOf2Itemsets.size(); i++) {
                System.out.println("N-list " + i + ":");
                for (String itemset : master.nListsOf2Itemsets.get(i)) {
                    System.out.println(itemset);
                }
            }

            // Step 3: Send frequent items and 2-itemsets to slaves
            System.out.println("Sending frequent items and 2-itemsets to slaves...");
            master.sendFrequentItemsAnd2ItemsetsToSlaves();

            // Step 4: Distribute tasks to slaves
            System.out.println("Distributing tasks to slaves...");
            master.distributeTasks();

            // Step 5: Receive frequent itemsets from slaves
            System.out.println("Receiving frequent itemsets from slaves...");
            master.receiveFrequentItemsetsFromSlaves();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

