
import java.util.ArrayList;
import java.util.List;

public class task {
    private int ID;
    private double size; // Task size from CSV
    private double rank; // Task rank from CSV
    private List<Integer> pre; // List of predecessor task IDs
    private List<Integer> succ; // List of successor task IDs
    
    // Constructor
    public task(int ID) {
        this.ID = ID;
        this.pre = new ArrayList<>();
        this.succ = new ArrayList<>();
    }
    
    // Getters and Setters
    public int getID() {
        return ID;
    }
    
    public void setID(int ID) {
        this.ID = ID;
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
    
    public void setPre(List<Integer> pre) {
        this.pre = pre;
    }
    
    public List<Integer> getSucc() {
        return succ;
    }
    
    public void setSucc(List<Integer> succ) {
        this.succ = succ;
    }
    
    // Utility methods
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
    
    public void removePredecessor(int taskID) {
        pre.remove(Integer.valueOf(taskID));
    }
    
    public void removeSuccessor(int taskID) {
        succ.remove(Integer.valueOf(taskID));
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
