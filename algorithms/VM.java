
import java.util.HashMap;
import java.util.Map;

public class VM {
    private int ID;
    private Map<String, Double> processingCapabilities; // Map of capability type to processing power
    private Map<Integer, Double> bandwidthToVMs; // Map of VM_ID to bandwidth value B(this_VM, target_VM)
    private boolean isActive;
    private int threshold; // Maximum number of tasks this VM can handle
    
    // Constructor
    public VM(int ID) {
        this.ID = ID;
        this.processingCapabilities = new HashMap<>();
        this.bandwidthToVMs = new HashMap<>();
        this.isActive = true;
        this.threshold = 1; // Default threshold
    }
    
    // Constructor with initial capabilities
    public VM(int ID, Map<String, Double> capabilities) {
        this.ID = ID;
        this.processingCapabilities = new HashMap<>(capabilities);
        this.bandwidthToVMs = new HashMap<>();
        this.isActive = true;
        this.threshold = 1; // Default threshold
    }
    
    // Constructor with threshold
    public VM(int ID, int threshold) {
        this.ID = ID;
        this.processingCapabilities = new HashMap<>();
        this.bandwidthToVMs = new HashMap<>();
        this.isActive = true;
        this.threshold = threshold;
    }
    
    // Getters and Setters
    public int getID() {
        return ID;
    }
    
    public void setID(int ID) {
        this.ID = ID;
    }
    
    public Map<String, Double> getProcessingCapabilities() {
        return processingCapabilities;
    }
    
    public void setProcessingCapabilities(Map<String, Double> processingCapabilities) {
        this.processingCapabilities = processingCapabilities;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        isActive = active;
    }
    
    public int getThreshold() {
        return threshold;
    }
    
    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }
    
    // Utility methods
    public void addCapability(String capabilityType, double processingPower) {
        processingCapabilities.put(capabilityType, processingPower);
    }

    // Alias for older code/tests
    public void addProcessingCapability(String capabilityType, double processingPower) {
        addCapability(capabilityType, processingPower);
    }
    
    public void removeCapability(String capabilityType) {
        processingCapabilities.remove(capabilityType);
    }
    
    public double getCapability(String capabilityType) {
        return processingCapabilities.getOrDefault(capabilityType, 0.0);
    }
    
    public boolean hasCapability(String capabilityType) {
        return processingCapabilities.containsKey(capabilityType);
    }
    
    public void updateCapability(String capabilityType, double newProcessingPower) {
        if (processingCapabilities.containsKey(capabilityType)) {
            processingCapabilities.put(capabilityType, newProcessingPower);
        }
    }
    
    // Bandwidth management methods
    public void setBandwidthToVM(int targetVMId, double bandwidth) {
        bandwidthToVMs.put(targetVMId, bandwidth);
    }
    
    public double getBandwidthToVM(int targetVMId) {
        return bandwidthToVMs.getOrDefault(targetVMId, 0.0);
    }
    
    public Map<Integer, Double> getAllBandwidths() {
        return new HashMap<>(bandwidthToVMs);
    }
    
    public void setBandwidthMap(Map<Integer, Double> bandwidthMap) {
        this.bandwidthToVMs = new HashMap<>(bandwidthMap);
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
                '}';
    }
}
