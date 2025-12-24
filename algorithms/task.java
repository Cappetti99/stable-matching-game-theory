
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class task {
    private int ID;
    private double size; // Task size from CSV
    private double rank; // Task rank from CSV
    private List<Integer> pre; // List of predecessor task IDs
    private List<Integer> succ; // List of successor task IDs

    // Optional: communication "size"/weight per edge (this -> succ)
    private Map<Integer, Double> succCommCost;
    
    // Constructor
    public task(int ID) {
        this.ID = ID;
        this.pre = new ArrayList<>();
        this.succ = new ArrayList<>();
        this.succCommCost = new HashMap<>();
    }

    // Getters
    public int getID() {
        return ID;
    }
    
    public double getSize() {
        return size;
    }
    
    public void setSize(double size) {
        this.size = size;
    }
    
    public double getRank() {
        return rank;
    }
    
    public void setRank(double rank) {
        this.rank = rank;
    }
    
    public List<Integer> getPre() {
        return pre;
    }
    
    public List<Integer> getSucc() {
        return succ;
    }
    
    // Relationship management methods
    public void addPredecessor(int taskID) {
        if (!pre.contains(taskID)) {
            pre.add(taskID);
        }
    }
    
    public void addSuccessor(int taskID) {
        if (!succ.contains(taskID)) {
            succ.add(taskID);
        }
    }

    /**
     * Overload to store an explicit communication cost/size for edge (this -> taskID).
     * The scheduler can interpret it as dataSize and combine with bandwidth.
     */
    public void addSuccessor(int taskID, double commCost) {
        addSuccessor(taskID);
        succCommCost.put(taskID, commCost);
    }

    public double getSuccCommunicationCost(int succTaskId) {
        return succCommCost.getOrDefault(succTaskId, -1.0);
    }

    @Override
    public String toString() {
        return "Task{" +
                "ID=" + ID +
                ", size=" + size +
                ", rank=" + rank +
                ", pre=" + pre +
                ", succ=" + succ +
                '}';
    }
}
