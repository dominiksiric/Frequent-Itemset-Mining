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

public class SlaveSimulator0 {
    private static MemoryLogger memoryLogger = MemoryLogger.getInstance();
    private static String localIPAddress;
    private static final int PORT = 9000;

    public static void main(String[] args) {
        try {
            Config.parse();
            localIPAddress = writeDownLocalIPAddress(Config.slave_address_directory, PORT);
            ServerSocket serverSocket = new ServerSocket(PORT);
            System.out.println("Slave IP Address: " + localIPAddress);
            System.out.println("Slave Port: " + PORT);
            System.out.println();

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("Received connection from master: " + socket);
                Slave slave = new Slave(socket);
                slave.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String writeDownLocalIPAddress(String IPAddressDirectory, int port) throws IOException {
        File dir = new File(IPAddressDirectory);
        if (!dir.exists()) dir.mkdirs();

        String ipAddress = InetAddress.getLocalHost().getHostAddress().trim();
        File file = new File(IPAddressDirectory + "/" + ipAddress + "_" + port);
        file.createNewFile();
        return ipAddress;
    }

    private static class Slave extends Thread {
        private Socket socket;
        private DataInputStream dis;
        private DataOutputStream dos;
        private PrePostPlusE alg;
        private int PART_COUNT;

        public Slave(Socket socket) {
            this.socket = socket;
            try {
                dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                int ID = dis.readInt();
                String[] parameters = dis.readUTF().split(" ");
                fromScratchMining(parameters, ID);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            } finally {
                close();
            }
        }

        private void fromScratchMining(String[] parameters, int ID) throws IOException, InterruptedException {
            long start = System.currentTimeMillis();

            String dataFileName = parameters[1];
            double threshold = Double.parseDouble(parameters[2]);
            PART_COUNT = Integer.parseInt(parameters[3]);

            System.out.println("Slave ID: " + ID);
            System.out.println("Data file name: " + dataFileName);
            System.out.println("Support threshold: " + threshold);
            System.out.println("Part count: " + PART_COUNT);
            System.out.println("-----------------------------------------------------------------------------");

            alg = new PrePostPlusE(Config.input_data_directory + dataFileName, threshold);

            achieveGlobalFrequentItems();

            System.out.println("-----------------------------------------------------------------------------");

            if (PART_COUNT < 2) {
                achieveGlobalFrequentItemsets(dataFileName);
            } else {
                achieveGlobalFrequentItemsetsPARTS(dataFileName);
            }

            System.out.println("-----------------------------------------------------------------------------");
            System.out.println("=> Totally local running time in (ms): " + (System.currentTimeMillis() - start));
            System.out.println("===================================FINISH====================================");
            System.out.println();
        }

        private void achieveGlobalFrequentItems() throws IOException, InterruptedException {
            long start = System.currentTimeMillis();

            System.out.println("=> Count support-count for items in (ms): " + alg.countSupportCountForItems());

            List<String> nameBasedOrderLocalFrequentItems = alg.getFrequentItemsInItemNameBasedOrder();
            System.out.println("Local frequent item count: " + nameBasedOrderLocalFrequentItems.size());

            dos.writeInt(alg.transactionCount());

            dos.writeInt(nameBasedOrderLocalFrequentItems.size());
            for (String item : nameBasedOrderLocalFrequentItems) dos.writeUTF(item);
            dos.flush();

            int itemCount = dis.readInt();
            List<String> nameBasedOrderGlobalPotentialItems = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) nameBasedOrderGlobalPotentialItems.add(dis.readUTF());

            int[] localSupportCounts = alg.getSupportCountOfGlobalPotentialItems(nameBasedOrderGlobalPotentialItems);

            for (int supportCount : localSupportCounts) dos.writeInt(supportCount);
            dos.flush();

            itemCount = dis.readInt();
            List<String> globalFrequentItems = new ArrayList<>(itemCount);
            for (int i = 0; i < itemCount; i++) globalFrequentItems.add(dis.readUTF());

            System.out.println("Global frequent item count: " + itemCount);
            System.out.println("=> Prepare to build the local PPC tree in (ms): " + alg.prepareToConstructPPCTree(globalFrequentItems));

            System.out.println("Total time for global frequent items: " + (System.currentTimeMillis() - start));
        }

        private void achieveGlobalFrequentItemsets(String dataFileName) throws IOException, InterruptedException {
            System.out.println("=> Build the local PPC tree in (ms): " + alg.constructPPCTree());
            System.out.println("=> Mine local frequent itemsets in (ms): " + alg.mining());

            FPOTree fpmTree = new FPOTree();
            fpmTree.build_from_1IS(alg.itemCodesMaptoFrequencies, alg.supportCountThreshold());
            System.out.println("=> Time of building the local FPM tree in (ms): " + fpmTree.build_from_kIS(alg.hFrequentPatterns, false));

            System.out.println("=> Time of counting all kinds of nodes in (ms): " + fpmTree.count_nodes());
            System.out.println("=> Time of sending the local FPM tree in (ms): " + fpmTree.send_only_itemCode(dos));

            System.out.println("-------------------The information of the local FPM tree---------------------");
            System.out.println("Total node count: " + fpmTree.getNodeCount());
            System.out.println("\tInner node count: " + fpmTree.getInnerNodeCount());
            System.out.println("\tLeaf node count: " + fpmTree.getLeafNodeCount());

            memoryLogger.checkMemory();
            System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());

            fpmTree.free();
            fpmTree = new FPOTree();
            System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());

            System.out.println("=> Time of receiving the global FPM tree in (ms): " + fpmTree.receive_only_itemCode(dis));

            System.out.println("=> Time of updating support counts in (ms): " + fpmTree.update_supportCount(alg));
            System.out.println("=> Time of sending the array of support counts in (ms): " + fpmTree.send_supportCountArray_toMaster(dos));

            memoryLogger.checkMemory();
            System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());
        }

        private void achieveGlobalFrequentItemsetsPARTS(String dataFileName) throws IOException, InterruptedException {
            System.out.println("=> Build the local PPC tree in (ms): " + alg.constructPPCTree());
            System.out.println("=> Mine local frequent 2-itemsets in (ms): " + alg.mining_frequent_2itemsets());

            FPOTree fpmTree = new FPOTree();
            System.out.println("=> Build FPM-Tree from local frequent 2-itemsets in (ms): " +
                    fpmTree.build_from_kIS(alg.hFrequentPatterns, true));
            fpmTree.count_nodes();

            System.out.println("Send FPM-Tree of local frequent 2-itemsets to Master in (ms): " + fpmTree.send_only_itemCode(dos));
            fpmTree.free();
            fpmTree = new FPOTree();
            System.out.println("Wait and receive global FPM-Tree of potential 2-itemsets in (ms): " + fpmTree.receive_only_itemCode(dis));
            System.out.println("The number of potential 2-itemsets: " + fpmTree.getLeafNodeCount());

            String[][] potential_2itemset_2D = fpmTree.get_2itemsets(PART_COUNT);
            fpmTree.free();
            fpmTree = null;

            System.out.println();
            System.out.println("------------------LOOP of Achieving Global Frequent Itemsets-----------------");
            for (int i = 0; i < PART_COUNT; i++) {
                System.out.println("----------------------------------PART " + i + "-------------------------------------");
                mineFrequentKItemsetsPARTBuildAndSendFPMTree(potential_2itemset_2D[i]);
                System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
                receiveUpdateSendGlobalFPMTree();
                System.out.println("GC time in (ms): " + GarbageCollector.collectMemory());
            }
        }

        private void mineFrequentKItemsetsPARTBuildAndSendFPMTree(String[] potential_2itemsets) throws InterruptedException, IOException {
            long start = System.currentTimeMillis();

            Map<String, FPSubset> hFrequentPatterns_PART = new HashMap<>(potential_2itemsets.length);
            System.out.println("=> Time of discovering subspaces of frequent k-itemsets in (ms): " +
                    alg.mining_frequent_kItemsets_PART(potential_2itemsets, hFrequentPatterns_PART));

            FPOTree fpmTree = new FPOTree();
            System.out.println("=> Time of building the local FPM tree in (ms): " +
                    fpmTree.build_from_kIS(hFrequentPatterns_PART, false));

            System.out.println("=> Time of counting all kinds of nodes in (ms): " + fpmTree.count_nodes());
            System.out.println("=> Time of sending the local FPM tree in (ms): " + fpmTree.send_only_itemCode(dos));

            System.out.println("--------------------The information of the local FPM tree---------------------");
            System.out.println("Total node count: " + fpmTree.getNodeCount());
            System.out.println("\tInner node count: " + fpmTree.getInnerNodeCount());
            System.out.println("\tLeaf node count: " + fpmTree.getLeafNodeCount());

            memoryLogger.checkMemory();
            System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());

            long totalProcessingTime = System.currentTimeMillis() - start;
            System.out.println("Total time for building and sending local FPM tree: " + totalProcessingTime);
        }

        private void receiveUpdateSendGlobalFPMTree() throws InterruptedException, IOException {
            long start = System.currentTimeMillis();

            FPOTree fpmTree = new FPOTree();
            long receiveTime = fpmTree.receive_only_itemCode(dis);
            System.out.println("=> Time of waiting the global FPM tree in (ms): " +
                    (System.currentTimeMillis() - start - receiveTime));
            System.out.println("=> Time of receiving the global FPM tree in (ms): " + receiveTime);

            System.out.println("=> Time of updating support counts in (ms): " + fpmTree.update_supportCount(alg));
            System.out.println("=> Time of sending the array of support counts in (ms): " + fpmTree.send_supportCountArray_toMaster(dos));

            memoryLogger.checkMemory();
            System.out.println("=> Memory used in (MB): " + memoryLogger.getMaxUsedMemory());

            alg.free();

            long totalProcessingTime = System.currentTimeMillis() - start;
            System.out.println("Total time for receiving, updating, and sending global FPM tree: " + totalProcessingTime);
        }

        private void close() {
            try {
                if (dis != null) dis.close();
                if (dos != null) dos.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}