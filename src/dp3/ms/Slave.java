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
public class Slave extends Thread {
    private Socket socket;
    private DataInputStream dis;
    private DataOutputStream dos;
    private PrePostPlusE alg;

    public Slave(Socket socket, PrePostPlusE alg) {
        this.socket = socket;
        this.alg = alg;
        try {
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                // Receive task request from master
                int taskRequest = dis.readInt();
                if (taskRequest == Master.NO_MORE_TASK) {
                    // No more tasks from master, send finish signal and stop
                    dos.writeInt(Master.NO_MORE_TASK);
                    dos.flush();
                    break;
                }

                // Receive
                String task = dis.readUTF();

                discoverFrequentKItemsets(task);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    private void discoverFrequentKItemsets(String task) {
        try {
            String[] taskItems = task.split(" ");
            String item1 = taskItems[0];
            String item2 = taskItems[1];

            // Discover frequent k-itemsets for the given 2-itemset
            String[] potential_2itemsets = new String[]{item1 + " " + item2};
            long start = System.currentTimeMillis();
            Map<String, FPSubset> hFrequentPatterns_PART = new HashMap<>(potential_2itemsets.length);
            long miningTime = alg.mining_frequent_kItemsets_PART(potential_2itemsets, hFrequentPatterns_PART);

            // Send the discovered frequent itemsets back to the master
            // Sending the number of frequent itemsets found
            dos.writeInt(hFrequentPatterns_PART.size());
            dos.flush();

            System.out.println("=> Time of discovering subspaces of frequent k-itemsets in (ms): " + miningTime);
            System.out.println("Performing task: " + task + " in (ms): " + (System.currentTimeMillis() - start));


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DataOutputStream getDataOutputStream() {
        return dos;
    }

    public DataInputStream getDataInputStream() {
        return dis;
    }

    public void close() {
        try {
            if (dis != null) dis.close();
            if (dos != null) dos.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

