
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public class VM {
    private int ID;
    private Map<String, Double> processingCapabilities; // Map of capability type to processing power
    private Map<Integer, Double> bandwidthToVMs; // Map of VM_ID to bandwidth value B(this_VM, target_VM)
    private boolean isActive;
    private int threshold; // Maximum number of tasks this VM can handle per level
    private List<Integer> waitingList; // Tasks assigned to this VM
    
    // Constructor
    public VM(int ID) {
        this.ID = ID;
        this.processingCapabilities = new HashMap<>();
        this.bandwidthToVMs = new HashMap<>();
        this.isActive = true;
        this.threshold = 1; // Default threshold
        this.waitingList = new ArrayList<>();
    }
    
    // Getters
    public int getID() {
        return ID;
    }
    
    public Map<String, Double> getProcessingCapabilities() {
        return processingCapabilities;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public int getThreshold() {
        return threshold;
    }
    
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
    
    // WaitingList management
    public List<Integer> getWaitingList() {
        return waitingList;
    }
    
    public void addToWaitingList(int taskId) {
        waitingList.add(taskId);
    }
    
    public void clearWaitingList() {
        waitingList.clear();
    }
    
    public boolean isFull() {
        return waitingList.size() >= threshold;
    }
    
    public int getWaitingListSize() {
        return waitingList.size();
    }
    
    // Capability management
    public void addCapability(String capabilityType, double processingPower) {
        processingCapabilities.put(capabilityType, processingPower);
    }
    
    public double getCapability(String capabilityType) {
        return processingCapabilities.getOrDefault(capabilityType, 0.0);
    }
    
    // Bandwidth management
    public void setBandwidthToVM(int targetVMId, double bandwidth) {
        bandwidthToVMs.put(targetVMId, bandwidth);
    }
    
    public double getBandwidthToVM(int targetVMId) {
        return bandwidthToVMs.getOrDefault(targetVMId, 0.0);
    }
    
    public Map<Integer, Double> getAllBandwidths() {
        return new HashMap<>(bandwidthToVMs);
    }
    
    public boolean hasBandwidthToVM(int targetVMId) {
        return bandwidthToVMs.containsKey(targetVMId);
    }
    
    @Override
    public String toString() {
        return "VM{" +
                "ID=" + ID +
                ", processingCapabilities=" + processingCapabilities +
                ", bandwidthToVMs=" + bandwidthToVMs +
                ", isActive=" + isActive +
                ", threshold=" + threshold +
                ", waitingList=" + waitingList +
                '}';
    }
}
