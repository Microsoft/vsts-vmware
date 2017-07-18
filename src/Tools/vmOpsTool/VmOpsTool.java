import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class VmOpsTool {

    private Callable<IVMWare> vmwareFactory;

    public VmOpsTool(Callable<IVMWare> vmwareFactory) {
        this.vmwareFactory = vmwareFactory;
    }

    public static void main(String[] args) {
        try {
            new VmOpsTool(VMWareImpl::new).executeActionOnVmsInParallel(args);
        } catch (Exception exp) {
            System.err.println(exp.getMessage() != null ? exp.getMessage() : "Unknown error occurred");
            System.exit(1);
        }
    }

    /**
     * Duplicate keys, key missing, value missing all those cases we are not
     * expecting as the command construction is done by type script layer. Hence
     * parse logic is not handling any of these cases
     *
     * @param cmdArgs array of input command line options
     * @return map of key value pairs of input parameters
     */
    public static Map<String, String> parseCmdLine(String[] cmdArgs) {
        Map<String, String> argsMap = new HashMap<>();
        String key = null;
        String value;

        System.out.println("Parsing input parameters...");
        for (String arg : cmdArgs) {
            if (!arg.equals("") && arg.charAt(0) == '-') {
                key = arg;
            } else if (!arg.equals(Constants.VM_OPS_TOOL)) {
                value = arg;
                argsMap.put(key, value);
            }
        }
        return argsMap;
    }

    /**
     * parse the command line arguments and performs the vm operation
     *
     * @param args command line arguments
     * @throws Exception on failure
     */
    public void executeActionOnVmsInParallel(String[] args) throws Exception {

        Map<String, String> argsMap = parseCmdLine(args);

        String vCenterUrl = argsMap.get(Constants.V_CENTER_URL);
        String vCenterUserName = argsMap.get(Constants.V_CENTER_USER_NAME);
        String vCenterPassword = argsMap.get(Constants.V_CENTER_PASSWORD);
        String vmList = argsMap.get(Constants.VM_LIST);
        String targetDC = argsMap.get(Constants.TARGET_DC);
        boolean skipCACheck = Boolean.parseBoolean(argsMap.get(Constants.SKIP_CA_CHECK));
        ConnectionData connData = new ConnectionData(vCenterUrl, vCenterUserName, vCenterPassword, targetDC, skipCACheck);

        String[] vmNames = vmList.split(",");
        ExecutorService executor = Executors.newFixedThreadPool(vmNames.length);

        List<Future<ActionResult>> vmOperationResults = new ArrayList<>();

        try {
            for (String vmName : vmNames) {
                vmOperationResults.add(executor.submit(() -> executeActionOnAVm(argsMap, vmName.trim(), connData)));
            }
        } finally {
            executor.shutdown();
        }

        while (!executor.isTerminated()) {
            Thread.sleep(1000);
        }

        String failedVmList = "";
        for (Future<ActionResult> vmOperationResult : vmOperationResults) {
            failedVmList += vmOperationResult.get().getFailedVm();
        }

        if (!failedVmList.isEmpty()) {
            throw new Exception(String.format("%s [%s].", vmOperationResults.get(0).get().getErrorMessage(), failedVmList));
        }
    }

    private int parseTimeout(String timeout) throws Exception {
        try {
            return Integer.parseInt(timeout);
        } catch (NumberFormatException ex) {
            throw new Exception("Invalid timeout value ( " + timeout + " ) for the operation, please specify valid interger value for timeout");
        }
    }

    public ActionResult executeActionOnAVm(Map<String, String> argsMap, String vmName, ConnectionData connData) throws Exception {

        ActionResult actionResult = new ActionResult();

        if (argsMap.containsKey(Constants.SNAPSHOT_OPS)) {
            String actionName = argsMap.get(Constants.SNAPSHOT_OPS);
            String snapshotName = argsMap.get(Constants.SNAPSHOT_NAME);
            actionResult.setErrorMessage(String.format("Failed to [%s] snapshot [%s] on virtual machines ", actionName, snapshotName));
            actionResult.setFailedVm(executeSnapshotAction(argsMap, vmName, snapshotName, actionName, connData));
        } else if (argsMap.containsKey(Constants.CLONE_TEMPLATE)) {
            actionResult.setErrorMessage("Create vm from template operation failed for virtual machines ");
            actionResult.setFailedVm(executeCloneVmAction(argsMap, vmName, connData));
        } else if (argsMap.containsKey(Constants.DELETE_VM)) {
            actionResult.setErrorMessage("delete vm operation failed for virtual machines ");
            actionResult.setFailedVm(executeDeleteVmAction(vmName, connData));
        } else if (argsMap.containsKey(Constants.POWER_OPS)) {
            String actionName = argsMap.get(Constants.POWER_OPS);
            actionResult.setErrorMessage(String.format("Failed to [%s] virtual machines ", actionName));
            actionResult.setFailedVm(executePowerOpsAction(argsMap, vmName, actionName, connData));
        } else {
            System.out.println(String.format("##vso[task.logissue type=error;code=INFRA_InvalidOperation;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Invalid action input for the operation.");
        }

        return actionResult;
    }

    /**
     * @param vmName   name of the virtual machine
     * @param connData vCenter connection information
     * @return vmName if operation fails
     */
    private String executePowerOpsAction(Map<String, String> argsMap, String vmName, String actionName, ConnectionData connData) {
        String failedVm = "";
        try {
            switch (actionName) {
                case Constants.POWER_ON_VM_ACTION:
                    int timeout = parseTimeout(argsMap.get(Constants.TIMEOUT));
                    vmwareFactory.call().powerOnVM(vmName, timeout, connData);
                    break;
                case Constants.SHUTDOWN_VM_ACTION:
                    timeout = parseTimeout(argsMap.get(Constants.TIMEOUT));
                    vmwareFactory.call().shutdownVM(vmName, timeout, connData);
                    break;
                case Constants.POWER_OFF_VM_ACTION:
                    vmwareFactory.call().powerOffVM(vmName, connData);
                    break;
                default:
                    System.out.println(String.format(
                            "##vso[task.logissue type=error;code=INFRA_InvalidPowerOperation;TaskId=%s;]\n",
                            Constants.TASK_ID));
                    throw new Exception("Invalid action name ( " + actionName + " ) for power operation");
            }

        } catch (Exception exp) {
            System.out.println(exp.getMessage() != null ? exp.getMessage() : "Unknown error occurred.");
            failedVm = vmName + " ";
        }
        return failedVm;
    }

    /**
     * @param vmName   name of the virtual machine
     * @param connData vCenter connection information
     * @return vmName if operation fails
     */
    private String executeDeleteVmAction(String vmName, ConnectionData connData) {
        String failedVm = "";
        try {
            vmwareFactory.call().deleteVM(vmName, connData);
        } catch (Exception exp) {
            System.out.println(exp.getMessage() != null ? exp.getMessage() : "Unknown error occurred.");
            failedVm = vmName + " ";
        }
        return failedVm;
    }

    /**
     * @param argsMap  map of command line arguments
     * @param vmName   name of the virtual machine
     * @param connData vCenter connection information
     * @return vmName if operation fails
     */
    private String executeCloneVmAction(Map<String, String> argsMap, String vmName, ConnectionData connData) throws Exception {
        String failedVm = "";
        String templateName = argsMap.get(Constants.CLONE_TEMPLATE);
        String folder = argsMap.get(Constants.FOLDER);
        String computeType = argsMap.get(Constants.COMPUTE_TYPE);
        String computeName = argsMap.get(Constants.COMPUTE_NAME);
        String datastore = argsMap.get(Constants.DATASTORE);
        String customizationspec = argsMap.get(Constants.CUSTOMIZATIONSPEC);
        String description = argsMap.get(Constants.DESCRIPTION);
        int timeout = parseTimeout(argsMap.get(Constants.TIMEOUT));

        try {
            vmwareFactory.call().cloneVMFromTemplate(templateName, vmName, folder, computeType, computeName, datastore, 
                    customizationspec, description, timeout, connData);
        } catch (Exception exp) {
            System.out.println(exp.getMessage() != null ? exp.getMessage() : "Unknown error occurred.");
            failedVm = vmName + " ";
        }
        return failedVm;
    }

    /**
     * @param argsMap      map of command line arguments
     * @param vmName       name of the virtual machine
     * @param snapshotName name of the virtual machine snapshot
     * @param actionName   type of snapshot action
     * @param connData     vCenter connection information
     * @return vmName if operation fails
     */
    private String executeSnapshotAction(Map<String, String> argsMap, String vmName, String snapshotName, String actionName, ConnectionData connData) {
        String failedVm = "";
        try {
            switch (actionName) {
                case Constants.RESTORE_SNAPSHOT_ACTION:
                    int timeout = parseTimeout(argsMap.get(Constants.TIMEOUT));
                    vmwareFactory.call().restoreSnapshot(vmName, snapshotName, timeout, connData);
                    break;
                case Constants.CREATE_SNAPSHOT_ACTION:
                    String description = argsMap.get(Constants.DESCRIPTION);
                    timeout = parseTimeout(argsMap.get(Constants.TIMEOUT));
                    boolean saveVmMemory = Boolean.parseBoolean(argsMap.get(Constants.SAVE_VM_MEMORY));
                    boolean quiesceVmFs = Boolean.parseBoolean(argsMap.get(Constants.QUIESCE_VM_FS));

                    vmwareFactory.call().createSnapshot(vmName, snapshotName, saveVmMemory, quiesceVmFs, description, timeout,
                            connData);
                    break;
                case Constants.DELETE_SNAPSHOT_ACTION:
                    vmwareFactory.call().deleteSnapshot(vmName, snapshotName, connData);
                    break;
                default:
                    System.out.println(String.format(
                            "##vso[task.logissue type=error;code=INFRA_InvalidSnapshotOperation;TaskId=%s;]",
                            Constants.TASK_ID));
                    throw new Exception("Invalid action name ( " + actionName + " ) for snapshot operation");
            }
        } catch (Exception exp) {
            System.out.println(exp.getMessage() != null ? exp.getMessage() : "Unknown error occurred.");
            failedVm = vmName + " ";
        }
        return failedVm;
    }
}
