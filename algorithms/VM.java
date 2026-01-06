
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class VM {
    private int ID;
    private double processingCapacity; // Processing power in MIP [10, 20]
    private Map<Integer, Double> bandwidthToVMs; // Map of VM_ID to bandwidth value B(this_VM, target_VM)
    private boolean isActive;
    private int threshold; // Maximum number of tasks this VM can handle per level
    private List<Integer> waitingList; // Tasks assigned to this VM
    
    // Constructor
    public VM(int ID) {
        this.ID = ID;
        this.processingCapacity = 0.0;
        this.bandwidthToVMs = new HashMap<>();
        this.isActive = true;
        this.threshold = 1; // Default threshold
        this.waitingList = new ArrayList<>();
    }
    
    // Getters 
    public int getID() {
        return ID;
    }

    public double getProcessingCapacity() {
        return processingCapacity;
    }

    public Map<Integer, Double> getBandwidthToVMs() {
        return bandwidthToVMs;
    }

    public boolean isActive() {
        return isActive;
    }

    public int getThreshold() {
        return threshold;
    }

    public List<Integer> getWaitingList() {
        return waitingList;
    }

    // Setters

    public void setProcessingCapacity(double processingCapacity) {
        this.processingCapacity = processingCapacity;
    }

    public void setBandwidthToVMs(Map<Integer, Double> bandwidthToVMs) {
        this.bandwidthToVMs = bandwidthToVMs;
    }

    public void setBandwidthToVM(int targetVMId, double bandwidth) {
        this.bandwidthToVMs.put(targetVMId, bandwidth);
    }

    public double getBandwidthToVM(int targetVMId) {
        return bandwidthToVMs.getOrDefault(targetVMId, 0.0);
    }

    public boolean hasBandwidthToVM(int targetVMId) {
        return bandwidthToVMs.containsKey(targetVMId);
    }

    public void setActive(boolean isActive) {
        this.isActive = isActive;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    public void setWaitingList(List<Integer> waitingList) {
        this.waitingList = waitingList;
    }

    // Add a task to the waiting list
    public void addToWaitingList(int taskId) {
        this.waitingList.add(taskId);
    }

    // Remove a task from the waiting list
    public void removeTaskFromWaitingList(int taskId) {
        this.waitingList.remove(Integer.valueOf(taskId));
    }

    public void clearWaitingList() {
        this.waitingList.clear();
    }

    public boolean isFull() {
        return waitingList.size() >= threshold;
    }

    public int getWaitingListSize() {
        return waitingList.size();
    }       

    @Override
    public String toString() {
        return "VM{" +
                "ID=" + ID +
                ", processingCapacity=" + processingCapacity +
                ", bandwidthToVMs=" + bandwidthToVMs +
                ", isActive=" + isActive +
                ", threshold=" + threshold +
                ", waitingList=" + waitingList +
                '}';
    }
}
