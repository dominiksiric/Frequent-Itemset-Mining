package dp3.ms;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dfism.commons.Config;
import dfism.commons.Config.MiningModes;
import dfism.commons.GarbageCollector;
import dfism.commons.MemoryLogger;
import dfism.fpo.FPOTree;
import dfism.fpo.FPSubset;
import dp3.p3e.PrePostPlusE;


// Note: Most parts of this algorithm follow a similair structure as the DP3 algorithm, this is for
// making imports more convinient. Parts are reused from DP3 in both the Master and Slave.
public class Slave {

    // From DP3

    private static MemoryLogger memoryLogger = MemoryLogger.getInstance();

    private static String LocalIPAddress;
    private static ServerSocket serverSocket;
    private static Socket socket;
    private static DataInputStream dis;
    private static DataOutputStream dos;

    private static PrePostPlusE alg;
    private static double threshold;
    private static int PART_COUNT;
    private static int ID;

    private static List<String> globalFrequentItems; // To store global frequent items received from the master
    private static List<List<String>> globalNLists;   // To store global N-lists of 2-itemsets received from the master

    public static void main(String[] args) {
        try{
            Config.parse();

            if(Config.is_distributed_file_system){
                System.out.println("Slave uses global distributed file system for partial datasets.");
                LocalIPAddress = writeDownLocalIPAddress(Config.slave_address_directory, Config.slave_port);
            }else{
                System.out.println("Nodes use their own local disks for partial datasets.");
                LocalIPAddress = InetAddress.getLocalHost().getHostAddress().trim();
            }

            serverSocket = new ServerSocket(Config.slave_port);
            System.out.println("Slave IP Address: " + LocalIPAddress);
            System.out.println("Slave Port: " + Config.slave_port);
            System.out.println();

            //Listen a request from the master
            socket = serverSocket.accept();
            dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            ID = dis.readInt();
            String[] parameters = dis.readUTF().split(" ");

            //Get the mining option
            MiningModes miningMode = MiningModes.valueOf(parameters[0].toUpperCase());
            switch(miningMode){
                case FROM_SCRATCH:
                    fromScratch_Mining(parameters);
                    break;
                case EXIT:
                    System.out.println("Exit.");
                    return;
                default:
                    return;
            }
        } catch(Exception e){
            try {serverSocket.close();} catch (IOException ioe) {ioe.printStackTrace();}
            e.printStackTrace();
        }
    }

    private static String writeDownLocalIPAddress(String IPAddressDirectory, int port) throws IOException {
        File dir = new File(IPAddressDirectory);
        if(!dir.exists()) dir.mkdirs();

        String ipAddress = InetAddress.getLocalHost().getHostAddress().trim();
        File file = new File(IPAddressDirectory+"/"+ipAddress+"_"+port);
        file.createNewFile();
        return ipAddress;
    }

    private static void fromScratchMining(String[] parameters) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();

        String dataFileName = parameters[1];
        threshold = Double.parseDouble(parameters[2]);
        PART_COUNT = Integer.parseInt(parameters[3]);

        System.out.println("Slave ID: " + ID);
        System.out.println("Data file name: " + dataFileName);
        System.out.println("Support threshold: " + threshold);
        System.out.println("Part count: " + PART_COUNT);
        System.out.println("-----------------------------------------------------------------------------");

        alg = new PrePostPlusE(Config.input_data_directory + dataFileName, threshold);

        achieveGlobalFrequentItems();

        System.out.println("-----------------------------------------------------------------------------");

        if (PART_COUNT < 2) achieveGlobalFrequentItemsets(dataFileName);
        else achieveGlobalFrequentItemsetsParts(dataFileName);

        System.out.println("-----------------------------------------------------------------------------");
        System.out.println("=> Totally local running time in (ms): " + (System.currentTimeMillis() - start));
        System.out.println("===================================FINISH====================================");
        System.out.println();
    }

    // Receiving frequent items and N-lists of 2-itemsets (later: achieving frequent global frequent items)
    private static long achieveGlobalFrequentItems() throws IOException, InterruptedException {
        long start = System.currentTimeMillis();

        System.out.println("=> Count support-count for items in (ms): " + alg.countSupportCountForItems());

        List<String> nameBasedOrderLocalFrequentItems = alg.getFrequentItemsInItemNameBasedOrder();
        System.out.println("Local frequent item count: " + nameBasedOrderLocalFrequentItems.size());

        // Sends local count of transactions
        dos.writeInt(alg.transactionCount());

        // Send local frequent items
        dos.writeInt(nameBasedOrderLocalFrequentItems.size());    // Send the number of items.
        for (String item : nameBasedOrderLocalFrequentItems) dos.writeUTF(item);    // Send items
        dos.flush();

        // Receiving frequent items
        int item_count = dis.readInt();
        globalFrequentItems = new ArrayList<>(item_count);
        for (int i = 0; i < item_count; i++) globalFrequentItems.add(dis.readUTF());

        System.out.println("Global frequent item count: " + item_count);

        // Receive N-lists of 2-itemsets
        int nListsCount = dis.readInt();
        globalNLists = new ArrayList<>(nListsCount);
        for (int i = 0; i < nListsCount; i++) {
            int nListSize = dis.readInt();
            List<String> nList = new ArrayList<>(nListSize);
            for (int j = 0; j < nListSize; j++) {
                nList.add(dis.readUTF());
            }
            globalNLists.add(nList);
        }

        System.out.println("Global N-lists count: " + nListsCount);

        return System.currentTimeMillis() - start;
    }

    // to be implemented
    private static void achieveGlobalFrequentItemsets(String dataFileName) throws IOException, InterruptedException {
        achieveGlobalFrequentItems();

    }

    // to be implemented (SELF NOTE: check DP3)
    private static void achieveGlobalFrequentItemsetsParts(String dataFileName) throws IOException, InterruptedException {
        achieveGlobalFrequentItems();

    }

}

