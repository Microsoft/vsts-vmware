import com.sun.xml.ws.client.BindingProviderProperties;
import com.vmware.vim25.*;

import javax.xml.ws.BindingProvider;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.lang.Thread.sleep;

public class VMWareImpl implements IVMWare {

    // vCenter Managed Object Types
    private final String VIRTUAL_MACHINE = "VirtualMachine";
    private final String DATA_CENTER = "Datacenter";
    private final String HOST_SYSTEM = "HostSystem";
    private final String CLUSTER_COMPUTE_RESOURCE = "ClusterComputeResource";
    private final String RESOURCE_POOL = "ResourcePool";
    private final String DATA_STORE = "Datastore";

    // vCenter Managed Object properties
    private final String VM_FOLDER = "vmFolder";
    private final String PARENT = "parent";
    private final String RESOURCE_POOL_PROP = "resourcePool";
    private final String SNAPSHOT = "snapshot";
    private final String CONFIG = "config";
    private final String NAME = "name";
    private final String GUEST_TOOLS_RUNNING_STATUS = "guest.toolsRunningStatus";
    private final String GUEST_TOOLS_VERSION_STATUS = "guest.toolsVersionStatus";
    private final String GUEST_OS_FAMILY = "guest.guestFamily";
    private final String GUEST_HEART_BEAT_STATUS = "guestHeartbeatStatus";
    private final String INFO_STATE = "info.state";
    private final String INFO_ERROR = "info.error";
    private final String LATEST_PAGE = "latestPage";
    private final String CUSTOMIZATION_SUCCEEDED = "CustomizationSucceeded";
    private final String CUSTOMIZATION_FAILED = "CustomizationFailed";

    private VimPortType vimPort;
    private ServiceContent serviceContent;
    private UserSession userSession;
    private ManagedObjectReference targetDCMor;

    public void connect(ConnectionData connData) throws Exception {
        try {
            if (!isSessionActive()) {
                System.out.println(String.format("No active session found.. establishing new session with '%s'.", connData.getUrl()));
                VimService vimService = new VimService();
                vimPort = vimService.getVimPort();

                Map<String, Object> reqContext = ((BindingProvider) vimPort).getRequestContext();
                reqContext.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, connData.getUrl());
                reqContext.put(BindingProvider.SESSION_MAINTAIN_PROPERTY, true);
                reqContext.put(BindingProviderProperties.REQUEST_TIMEOUT, 30 * 60 * 1000);
                reqContext.put(BindingProviderProperties.CONNECT_TIMEOUT, 5 * 60 * 1000);
                ManagedObjectReference serviceInstance = new ManagedObjectReference();
                serviceInstance.setType("ServiceInstance");
                serviceInstance.setValue("ServiceInstance");

                if (connData.isSkipCACheck()) {
                    SkipCACheck.AllowUntrustedConnections();
                }

                serviceContent = vimPort.retrieveServiceContent(serviceInstance);
                ManagedObjectReference rootFolder = serviceContent.getRootFolder();
                userSession = vimPort.login(serviceContent.getSessionManager(), connData.getUserName(), connData.getPassword(),
                        null);
                System.out.println(String.format("Searching for datacenter with name [ %s ].", connData.getTargetDC()));
                targetDCMor = getMorByName(rootFolder, connData.getTargetDC(), DATA_CENTER, false);
            }
        } catch (Exception exp) {
            System.out.println(String.format("##vso[task.logissue type=error;code=USERINPUT_ConnectionFailed;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Failed to connect: " + exp.getMessage());
        }
        System.out.println("Successfully established session with vCenter server.");
    }

    public void cloneVMFromTemplate(String templateName, String vmName, String computeType,
                                    String computeName, String targetDS, String customizationSpec,
                                    String description, int timeout, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println(String.format("Finding template [ %s ] on vCenter server.", templateName));
        ManagedObjectReference templateMor = getMorByName(targetDCMor, templateName, VIRTUAL_MACHINE, true);
        ManagedObjectReference targetVmFolder = (ManagedObjectReference) getMorProperties(targetDCMor, new String[]{VM_FOLDER}).get(VM_FOLDER);
        VirtualMachineCloneSpec cloneSpec = getVirtualMachineCloneSpec(computeType, computeName, targetDS, customizationSpec, description);

        System.out.println(String.format("Creating new virtual machine [ %s ] using template [ %s ].", vmName, templateName));
        ManagedObjectReference task = vimPort.cloneVMTask(templateMor, targetVmFolder, vmName, cloneSpec);

        if (waitAndGetTaskResult(task)) {
            System.out.println(String.format("Successfully created virtual machine [ %s ] using template [ %s ].", vmName, templateName));
        } else {
            throw new Exception(
                    String.format("Failed to create virtual machine [ %s ] using template [ %s ].", vmName, templateName));
        }

        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        if ((customizationSpec != null) && !customizationSpec.isEmpty()) {
            waitForOSCustomization(vmName, vmMor, Constants.OS_CUSTOMIZATION_MAX_WAIT_IN_MINUTES);
        }
        waitForVMToBeDeployReady(vmName, vmMor, timeout);
    }

    public void createSnapshot(String vmName, String snapshotName, boolean saveVMMemory, boolean quiesceFs,
                               String description, int timeout, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println(String.format("Creating snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        ManagedObjectReference task = vimPort.createSnapshotTask(vmMor, snapshotName, description, saveVMMemory,
                quiesceFs);

        if (waitAndGetTaskResult(task)) {
            System.out.println(String.format("Successfully created snapshot [ %s ] On virtual machine [ %s ].", snapshotName, vmName));
        } else {
            throw new Exception(
                    String.format("Failed to create snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        }

        if (isVMPoweredOn(vmName, true, connData)) {
            waitForVMToBeDeployReady(vmName, vmMor, timeout);
        }
    }

    public void restoreSnapshot(String vmName, String snapshotName, int timeout, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println(String.format("Restoring snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        ManagedObjectReference cpMor = getSnapshotReference(vmMor, vmName, snapshotName);
        ManagedObjectReference task = vimPort.revertToSnapshotTask(cpMor, null, false);

        if (waitAndGetTaskResult(task)) {
            System.out.println(String.format("Successfully reverted to snapshot [ %s ] On virtual machine [ %s ].", snapshotName,
                    vmName));
        } else {
            throw new Exception(
                    String.format("Failed to revert snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        }
        waitForVMToBeDeployReady(vmName, vmMor, timeout);
    }

    public void deleteSnapshot(String vmName, String snapshotName, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println(String.format("Deleting snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        ManagedObjectReference cpMor = getSnapshotReference(vmMor, vmName, snapshotName);
        ManagedObjectReference task = vimPort.removeSnapshotTask(cpMor, false, true);

        if (waitAndGetTaskResult(task)) {
            System.out.println(String.format("Successfully deleted snapshot [ %s ] On virtual machine [ %s ].", snapshotName, vmName));
        } else {
            throw new Exception(
                    String.format("Failed to delete snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        }
    }

    public void powerOnVM(String vmName, int timeout, ConnectionData connData) throws Exception {
        connect(connData);

        if (!isVMPoweredOn(vmName, false, connData)) {
            ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
            ManagedObjectReference task = vimPort.powerOnVMTask(vmMor, null);

            if (!waitAndGetTaskResult(task)) {
                throw new Exception(
                        String.format("Failed to power on virtual machine [ %s ].", vmName));
            }
            waitForVMToBeDeployReady(vmName, vmMor, timeout);
            return;
        }
        System.out.println(String.format("Virtual machine [ %s ] is already running.", vmName));
    }

    public void shutdownVM(String vmName, int timeout, ConnectionData connData) throws Exception {
        connect(connData);
        if (isVMPoweredOn(vmName, true, connData)) {
            ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
            vimPort.shutdownGuest(vmMor);

            ExecutorService threadPool = Executors.newFixedThreadPool(1);
            Runnable task = () -> {
                try {
                    VMWareImpl.this.waitForPowerOffOperation(vmName, vmMor);
                } catch (Exception e) {
                    System.out.println("##vso[task.debug] Failed to wait for vm [ " + vmName + " ] to be powered off. Failure reason: " + e.getMessage());
                }
            };

            threadPool.submit(task);
            threadPool.shutdown();
            if (!threadPool.awaitTermination(timeout, TimeUnit.SECONDS)) {
                System.out.println("Virtual machine [ " + vmName + " ] did not shutdown within given time, further deployment operation might fail.");
                threadPool.shutdownNow();
                if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                    System.out.println("##vso[task.debug] Operation didn't finish in time, hence exiting task with success");
                    System.exit(0);
                }
            }
            return;
        }
        System.out.println(String.format("Virtual machine [ %s ] is already shutdowned.", vmName));
    }

    public void powerOffVM(String vmName, ConnectionData connData) throws Exception {
        connect(connData);
        if (isVMPoweredOn(vmName, true, connData)) {
            ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
            ManagedObjectReference powerOffTask = vimPort.powerOffVMTask(vmMor);
            System.out.println(String.format("Waiting for virtual machine [ %s ] to power off.", vmName));
            if (!waitAndGetTaskResult(powerOffTask)) {
                throw new Exception(
                        String.format("Failed to power off virtual machine [ %s ].", vmName));
            }
            System.out.println(String.format("Successfully powered off the virtual machine [ %s ].", vmName));
            return;
        }
        System.out.println(String.format("Virtual machine [ %s ] is already powered off.", vmName));
    }

    public void deleteVM(String vmName, ConnectionData connData) throws Exception {
        connect(connData);
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        ManagedObjectReference task = vimPort.destroyTask(vmMor);

        if (waitAndGetTaskResult(task)) {
            System.out.println(String.format("Successfully delete virtual machine [ %s ] from vCenter Server.", vmName));
        } else {
            throw new Exception(
                    String.format("Failed to delete virtual machine [ %s ] from vCenter Server.", vmName));
        }
    }

    public String getCurrentSnapshot(String vmName, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println("Getting current snapshot name for virtual machine [ " + vmName + " ].");
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        return getCurrentSnapshotName(vmMor, vmName);
    }

    public boolean isSnapshotExists(String vmName, String snapshotName, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println(String.format("Finding snapshot [ %s ] on virtual machine [ %s ].", snapshotName, vmName));
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        try {
            getSnapshotReference(vmMor, vmName, snapshotName);
        } catch (Exception exp) {
            System.out.println(exp.getMessage());
            return false;
        }
        return true;
    }

    public boolean isVMExists(String vmName, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println(String.format("Finding virtual machine [ %s ] on vCenter server.", vmName));
        try {
            getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);
        } catch (Exception exp) {
            System.err.println(exp.getMessage());
            return false;
        }
        return true;
    }

    public boolean isVMPoweredOn(String vmName, boolean defaultValue, ConnectionData connData) throws Exception {
        connect(connData);
        System.out.println("Checking virtual machine [ " + vmName + " ] power status.");
        ManagedObjectReference vmMor = getMorByName(targetDCMor, vmName, VIRTUAL_MACHINE, false);

        VirtualMachineToolsVersionStatus toolsVersionStatus = VirtualMachineToolsVersionStatus.fromValue(
                (String) getMorProperties(vmMor, new String[]{GUEST_TOOLS_VERSION_STATUS}).get(GUEST_TOOLS_VERSION_STATUS));

        if (toolsVersionStatus.equals(VirtualMachineToolsVersionStatus.GUEST_TOOLS_NOT_INSTALLED)) {
            System.out.println("VMware tools are not installed, proceeding without checking poweron status");
            return defaultValue;
        }

        VirtualMachineToolsRunningStatus toolsRunningStatus = VirtualMachineToolsRunningStatus.fromValue(
                (String) getMorProperties(vmMor, new String[]{GUEST_TOOLS_RUNNING_STATUS}).get(GUEST_TOOLS_RUNNING_STATUS));
        return !(toolsRunningStatus.equals(VirtualMachineToolsRunningStatus.GUEST_TOOLS_NOT_RUNNING));
    }

    private boolean isGuestOSWindows(ManagedObjectReference vmMor) throws Exception {
        String guestFamily = (String) getMorProperties(vmMor, new String[]{GUEST_OS_FAMILY}).get(GUEST_OS_FAMILY);
        return guestFamily != null && guestFamily.toLowerCase().startsWith("windows");
    }

    private void waitForNetworkDiscoveryOfVM(String vmName, boolean isGuestOSWindows) throws Exception {
        boolean isDnsResolved = false;
        boolean isNetBIOSResolved = false;

        System.out.println(String.format("Waiting for virtual machine [ %s ] network discovery to complete. isGuestOSWindows = %s ", vmName, isGuestOSWindows));
        while (!(isDnsResolved && isNetBIOSResolved)) {
            sleep(Constants.POLLING_INTERVAL_IN_SECONDS * 1000);

            if (!isDnsResolved) {
                isDnsResolved = isDnsNameResolved(vmName);
            }

            if (!isNetBIOSResolved) {
                isNetBIOSResolved = isNetBIOSNameResolved(vmName, isGuestOSWindows);
            }
        }
        System.out.println("Network discovery of virtual machine [ " + vmName + " ] completed.");
    }

    private boolean isNetBIOSNameResolved(String vmName, boolean isGuestOSWindows) throws Exception {

        // When Automation Agent or guest OS type is linux NetBIOS resultion is not required
        if (!System.getProperty("os.name").toLowerCase().startsWith("windows") || !isGuestOSWindows) {
            return true;
        }

        String command = "cmd /c nbtstat -a " + vmName;
        try {

            Process child = Runtime.getRuntime().exec(command);
            child.waitFor();
            BufferedReader childStdOut = new BufferedReader(new InputStreamReader(child.getInputStream()));
            String output = "";
            String s;
            while ((s = childStdOut.readLine()) != null) {
                output += s.toLowerCase();
            }

            return output.contains(vmName.toLowerCase());

        } catch (Exception e) {
            // If host name is not registered in NetBIOS, this exception will be thrown hence ignoring.
        }

        return false;
    }

    private boolean isDnsNameResolved(String vmName) {

        try {
            String ipAddress = InetAddress.getByName(vmName).getHostAddress();
            return !ipAddress.isEmpty();
        } catch (UnknownHostException e) {
            // If host name is not registered in DNS or NIS, this exception will be thrown hence ignoring.
        }
        return false;
    }

    private ManagedObjectReference getMorByName(ManagedObjectReference rootContainer, String mobName, String morefType,
                                                boolean isTemplate) throws Exception {
        Map<String, List<ManagedObjectReference>> mobrMap = getObjectsInContainerByType(rootContainer, morefType, isTemplate);

        if (!mobrMap.containsKey(mobName.toLowerCase())) {
            System.out.println(String.format("##vso[task.logissue type=error;code=USERINPUT_ObjectNotFound;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception(morefType + " with name [ " + mobName + " ] not found.");
        }

        if (mobrMap.get(mobName.toLowerCase()).size() > 1) {
            System.out.println(String.format("##vso[task.logissue type=error;code=USERINPUT_DuplicateObjectsFound;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("There are more than one virtual machine exists with name [ " + mobName + " ]");
        }

        return mobrMap.get(mobName.toLowerCase()).get(0);
    }

    private void waitForVMToBeDeployReady(String vmName, ManagedObjectReference vmMor, int timeout) throws Exception {

        System.out.println("Waiting for virtual machine [ " + vmName + " ] to be deployment ready.");
        ExecutorService threadPool = Executors.newFixedThreadPool(1);
        Runnable task = () -> {
            try {
                VMWareImpl.this.waitForPowerOnOperation(vmName, vmMor);
                boolean isGuestOSWindows = isGuestOSWindows(vmMor);
                VMWareImpl.this.waitForNetworkDiscoveryOfVM(vmName, isGuestOSWindows);
            } catch (Exception e) {
                System.out.println("##vso[task.debug] Failed to wait for vm [ " + vmName + " ] to be deployment ready: Failure reason :" + e.getMessage());
            }
        };

        threadPool.submit(task);
        threadPool.shutdown();
        if (!threadPool.awaitTermination(timeout, TimeUnit.SECONDS)) {
            System.out.println("Virtual machine [ " + vmName + " ] deployment requirements not finished within given time, continuing further deployment operation might fail.");
            threadPool.shutdownNow();
            if (!threadPool.awaitTermination(60, TimeUnit.SECONDS)) {
                System.out.println("##vso[task.debug] Operation didn't finish in time, hence exiting task with success");
                System.exit(0);
            }
            return;
        }
        System.out.println("Virtual machine [ " + vmName + " ] is now ready for deployment.");
    }

    private void waitForPowerOffOperation(String vmName, ManagedObjectReference vmMor) throws Exception {
        System.out.println(String.format("Waiting for virtual machine [ %s ] to shutdown.", vmName));

        waitOnMorProperties(vmMor, new String[]{GUEST_TOOLS_RUNNING_STATUS}, new String[]{GUEST_TOOLS_RUNNING_STATUS},
                new Object[][]{new Object[]{VirtualMachineToolsRunningStatus.GUEST_TOOLS_NOT_RUNNING.value()}});
        waitOnMorProperties(vmMor, new String[]{GUEST_HEART_BEAT_STATUS}, new String[]{GUEST_HEART_BEAT_STATUS},
                new Object[][]{new Object[]{ManagedEntityStatus.GRAY}});

        System.out.println(String.format("Successfully shutdowned the virtual machine [ %s ].", vmName));
    }

    private void waitForPowerOnOperation(String vmName, ManagedObjectReference vmMor) throws Exception {
        System.out.println(String.format("Waiting for virtual machine [ %s ] to start.", vmName));

        waitOnMorProperties(vmMor, new String[]{GUEST_TOOLS_RUNNING_STATUS}, new String[]{GUEST_TOOLS_RUNNING_STATUS},
                new Object[][]{new Object[]{VirtualMachineToolsRunningStatus.GUEST_TOOLS_RUNNING.value()}});
        waitOnMorProperties(vmMor, new String[]{GUEST_HEART_BEAT_STATUS}, new String[]{GUEST_HEART_BEAT_STATUS},
                new Object[][]{new Object[]{ManagedEntityStatus.GREEN}});

        System.out.println(String.format("Successfully powered on virtual machine [ %s ].", vmName));
    }

    private boolean waitAndGetTaskResult(ManagedObjectReference task) throws Exception {
        boolean retVal = false;

        System.out.println("Waiting for operation completion.");
        Object[] result = waitOnMorProperties(task, new String[]{INFO_STATE, INFO_ERROR}, new String[]{INFO_STATE},
                new Object[][]{new Object[]{TaskInfoState.SUCCESS, TaskInfoState.ERROR}});

        if (result[0].equals(TaskInfoState.SUCCESS)) {
            retVal = true;
        }

        if (result[1] instanceof LocalizedMethodFault) {
            throw new Exception(((LocalizedMethodFault) result[1]).getLocalizedMessage());
        }
        return retVal;
    }

    private Object[] waitOnMorProperties(ManagedObjectReference Mor, String[] filterProps, String[] endWaitProps,
                                         Object[][] expectedVals) throws Exception {
        try {

            Object[] endVals = new Object[endWaitProps.length];
            Object[] filterVals = new Object[filterProps.length];
            String version = "";
            PropertyFilterSpec filterSpec = createPropFilterSpecForObject(Mor, filterProps);
            ManagedObjectReference propertyFilter = vimPort.createFilter(serviceContent.getPropertyCollector(), filterSpec, true);
            boolean reached = false;
            WaitOptions waitOptions = new WaitOptions();

            while (!reached) {
                UpdateSet updateSet = vimPort.waitForUpdatesEx(serviceContent.getPropertyCollector(), version, waitOptions);
                if (updateSet == null || updateSet.getFilterSet() == null) {
                    continue;
                }
                version = updateSet.getVersion();
                List<PropertyFilterUpdate> filterUpdateList = updateSet.getFilterSet();

                for (PropertyFilterUpdate filterUpdate : filterUpdateList) {
                    if (filterUpdate == null || filterUpdate.getObjectSet() == null) {
                        continue;
                    }
                    List<ObjectUpdate> objectUpdateList = filterUpdate.getObjectSet();
                    for (ObjectUpdate objectUpdate : objectUpdateList) {
                        if (objectUpdate == null || objectUpdate.getChangeSet() == null) {
                            continue;
                        }
                        List<PropertyChange> propChangeList = objectUpdate.getChangeSet();
                        for (PropertyChange propChange : propChangeList) {
                            updateValues(endWaitProps, endVals, propChange);
                            updateValues(filterProps, filterVals, propChange);
                        }
                    }
                }

                Object expectedVal;
                for (int chgi = 0; chgi < endVals.length && !reached; ++chgi) {
                    for (int vali = 0; vali < expectedVals[chgi].length && !reached; ++vali) {
                        expectedVal = expectedVals[chgi][vali];
                        reached = expectedVal.equals(endVals[chgi]);
                    }
                }
                Thread.sleep(Constants.POLLING_INTERVAL_IN_SECONDS * 1000);
            }
            vimPort.destroyPropertyFilter(propertyFilter);
            return filterVals;
        } catch (Exception exp) {
            System.out.println(String.format("##vso[task.logissue type=error;code=PREREQ_WaitForResultFailed;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Failed to get operation result: " + exp.getMessage());
        }
    }

    private void waitForOSCustomization(String vmName, ManagedObjectReference vmMor, int maxWaitTimeInMinutes) throws Exception {
        EventFilterSpec eventFilterSpec = getEventFilterSpecForVM(vmMor);
        ManagedObjectReference vmEventHistoryCollector = null;
        try {
            System.out.println(String.format("Waiting for virtual machine [ %s ] OS customization to complete.", vmName));
            vmEventHistoryCollector = vimPort.createCollectorForEvents(serviceContent.getEventManager(), eventFilterSpec);
            long startTime = System.currentTimeMillis();

            while ((new Date()).getTime() - startTime < maxWaitTimeInMinutes * 60 * 1000) {
                sleep(Constants.POLLING_INTERVAL_IN_SECONDS * 1000);
                ArrayList<Event> eventList = (ArrayList<Event>) ((ArrayOfEvent) getMorProperties(vmEventHistoryCollector, new String[]{LATEST_PAGE}).get(LATEST_PAGE)).getEvent();
                for (Event anEvent : eventList) {
                    String eventName = anEvent.getClass().getSimpleName();
                    if (eventName.equalsIgnoreCase(CUSTOMIZATION_SUCCEEDED)
                            || eventName.equalsIgnoreCase(CUSTOMIZATION_FAILED)) {
                        System.out.println("OS Customization for virtual machine [ " + vmName + " ] completed, with status: " + eventName);
                        return;
                    }
                }
            }
            System.out.println("OS Customization for virtual machine [ " + vmName + " ] didn't finish in time, continuing further.");
        } catch (Exception exp) {
            System.out.println(String.format("##vso[task.logissue type=error;code=PREREQ_WaitForOsCustomizationFailed;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Failed to wait for OS customization: " + exp.getMessage());
        } finally {
            vimPort.destroyCollector(vmEventHistoryCollector);
        }
    }

    private EventFilterSpec getEventFilterSpecForVM(ManagedObjectReference vmMor) {
        EventFilterSpecByEntity vmEntitySpec = new EventFilterSpecByEntity();
        vmEntitySpec.setEntity(vmMor);
        vmEntitySpec.setRecursion(EventFilterSpecRecursionOption.SELF);

        EventFilterSpec eventFilterSpec = new EventFilterSpec();
        eventFilterSpec.setEntity(vmEntitySpec);
        return eventFilterSpec;
    }

    private void updateValues(String[] endWaitProps, Object[] endVals, PropertyChange propChg) {
        for (int findi = 0; findi < endWaitProps.length; findi++) {
            if (propChg.getName().lastIndexOf(endWaitProps[findi]) >= 0) {
                if (propChg.getOp() == PropertyChangeOp.REMOVE) {
                    endVals[findi] = "";
                } else {
                    endVals[findi] = propChg.getVal();
                }
            }
        }
    }

    private ManagedObjectReference getSnapshotReference(ManagedObjectReference vmMor, String vmName,
                                                        String snapshotName) throws Exception {
        System.out.println(String.format("Querying snapshot information for virtual machine [ %s ].", vmName));
        VirtualMachineSnapshotInfo vmSnapshotInfo = (VirtualMachineSnapshotInfo) getMorProperties(vmMor,
                new String[]{SNAPSHOT}).get(SNAPSHOT);
        ManagedObjectReference snapshotMor;
        String snapshotNotFoundErr = "No snapshot found on virtual machine [ " + vmName + " ] with name " + snapshotName;

        if (vmSnapshotInfo != null) {
            List<VirtualMachineSnapshotTree> vmRootSnapshotList = vmSnapshotInfo.getRootSnapshotList();
            snapshotMor = findSnapshotInTree(vmRootSnapshotList, snapshotName);

            if (snapshotMor == null) {
                System.out.println(String.format("##vso[task.logissue type=error;code=USERINPUT_SnapshotNotFound;TaskId=%s;]",
                        Constants.TASK_ID));
                throw new Exception(snapshotNotFoundErr);
            }
        } else {
            System.out.println(String.format("##vso[task.logissue type=error;code=USERINPUT_SnapshotNotFound;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception(snapshotNotFoundErr);
        }
        return snapshotMor;
    }

    private String getCurrentSnapshotName(ManagedObjectReference vmMor, String vmName) throws Exception {
        System.out.println(String.format("Querying snapshot information for virtual machine [ %s ].", vmName));
        VirtualMachineSnapshotInfo vmSnapshotInfo = (VirtualMachineSnapshotInfo) getMorProperties(vmMor,
                new String[]{SNAPSHOT}).get(SNAPSHOT);
        String currentSnapshotName = "";

        if (vmSnapshotInfo != null) {
            System.out.println("Searching for current snapshot in snapshot tree.");
            List<VirtualMachineSnapshotTree> vmRootSnapshotList = vmSnapshotInfo.getRootSnapshotList();
            ManagedObjectReference currentSnapshotMor = vmSnapshotInfo.getCurrentSnapshot();
            currentSnapshotName = getCurrentSnapshotNameFromTree(vmRootSnapshotList, currentSnapshotMor);
        } else {
            System.out.println("Snapshot info is null!!.");
        }
        return currentSnapshotName;
    }

    private String getCurrentSnapshotNameFromTree(List<VirtualMachineSnapshotTree> vmRootSnapshotList,
                                                  ManagedObjectReference currentSnapshotMor) {
        String currentSnapshot = "";

        for (VirtualMachineSnapshotTree vmSnapshot : vmRootSnapshotList) {
            System.out.println(String.format("Current snapshot name: %s.", vmSnapshot.getName()));
            if (vmSnapshot.getSnapshot().getValue().equals(currentSnapshotMor.getValue())) {
                System.out.println("Found current snapshot in tree ( " + vmSnapshot.getName() + " ).");
                return vmSnapshot.getName();
            } else {
                List<VirtualMachineSnapshotTree> childTree = vmSnapshot.getChildSnapshotList();
                currentSnapshot = getCurrentSnapshotNameFromTree(childTree, currentSnapshotMor);
            }
        }

        return currentSnapshot;
    }

    private ManagedObjectReference findSnapshotInTree(List<VirtualMachineSnapshotTree> vmSnapshotList,
                                                      String snapshotName) {
        ManagedObjectReference snapshotMor = null;

        for (VirtualMachineSnapshotTree vmSnapshot : vmSnapshotList) {

            if (vmSnapshot.getName().equalsIgnoreCase(snapshotName)) {
                System.out.println(String.format("Found snapshot [ %s ] for virtual machine.", snapshotName));
                return vmSnapshot.getSnapshot();
            } else {
                List<VirtualMachineSnapshotTree> childTree = vmSnapshot.getChildSnapshotList();
                snapshotMor = findSnapshotInTree(childTree, snapshotName);
                if( snapshotMor != null)
                {
                    break;
                }
            }
        }
        return snapshotMor;
    }

    private Map<String, Object> getMorProperties(ManagedObjectReference mor, String[] propList) throws Exception {
        PropertyFilterSpec propFilterSpec = createPropFilterSpecForObject(mor, propList);
        RetrieveResult results;
        try {
            results = vimPort.retrievePropertiesEx(serviceContent.getPropertyCollector(), Collections.singletonList(propFilterSpec),
                    new RetrieveOptions());
        } catch (Exception exp) {
            System.out.println(String.format("##vso[task.logissue type=error;code=PREREQ_RetriveObjectPropertiesFailed;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Failed to properties for managed object : " + exp.getMessage());
        }
        final Map<String, Object> propMap = new HashMap<>();
        if (results != null) {
            for (ObjectContent objContent : results.getObjects()) {
                List<DynamicProperty> properties = objContent.getPropSet();
                for (DynamicProperty property : properties) {
                    propMap.put(property.getName(), property.getVal());
                }
            }
        }
        return propMap;
    }

    private PropertyFilterSpec createPropFilterSpecForObject(ManagedObjectReference objectMor, String[] propList) {
        PropertySpec propSpec = new PropertySpec();
        propSpec.setType(objectMor.getType());
        propSpec.getPathSet().addAll(Arrays.asList(propList));
        propSpec.setAll(false);

        ObjectSpec objSpec = new ObjectSpec();
        objSpec.setObj(objectMor);
        objSpec.setSkip(false);

        PropertyFilterSpec propFilterSpec = new PropertyFilterSpec();
        propFilterSpec.getPropSet().add(propSpec);
        propFilterSpec.getObjectSet().add(objSpec);
        return propFilterSpec;
    }

    private Map<String, List<ManagedObjectReference>> getObjectsInContainerByType(ManagedObjectReference container, String morefType,
                                                                                  boolean isTemplate) throws Exception {
        String filterProperty = morefType.equals(VIRTUAL_MACHINE) ? CONFIG : NAME;
        PropertyFilterSpec propertyFilterSpecs = createRecursiveFilterSpec(container, morefType,
                new String[]{filterProperty});

        try {
            System.out.println(String.format("Querying %s objects on vCenter server.", morefType));
            RetrieveResult results = vimPort.retrievePropertiesEx(serviceContent.getPropertyCollector(),
                    Collections.singletonList(propertyFilterSpecs), new RetrieveOptions());
            String token;
            final Map<String, List<ManagedObjectReference>> morMap = new HashMap<>();
            token = createMap(results, filterProperty, isTemplate, morMap);

            while (token != null && !token.isEmpty()) {
                results = vimPort.continueRetrievePropertiesEx(serviceContent.getPropertyCollector(), token);
                token = createMap(results, filterProperty, isTemplate, morMap);
            }
            return morMap;
        } catch (Exception exp) {
            System.out.println(String.format("##vso[task.logissue type=error;code=PREREQ_QueryObjectsFailed;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Failed to fetch objects: " + exp.getMessage());
        }
    }

    private String createMap(final RetrieveResult results, String filterProperty, boolean isTemplate, final Map<String, List<ManagedObjectReference>> morMap) throws Exception {
        String token = null;

        if (results != null) {
            token = results.getToken();
            for (ObjectContent objectContent : results.getObjects()) {
                ManagedObjectReference mor = objectContent.getObj();
                List<DynamicProperty> propertySet = objectContent.getPropSet();
                if (propertySet != null && !propertySet.isEmpty() && propertySet.get(0) != null) {
                    if (filterProperty.equals(CONFIG)) {
                        VirtualMachineConfigInfo vmConfig = (VirtualMachineConfigInfo) propertySet.get(0).getVal();
                        if (vmConfig != null && vmConfig.isTemplate() == isTemplate) {
                            UpdateMap(vmConfig.getName().toLowerCase(), mor, morMap);
                        }
                    } else {
                        String mobName = (String) propertySet.get(0).getVal();
                        UpdateMap(mobName.toLowerCase(), mor, morMap);
                    }
                }
            }
        }
        return token;
    }

    private void UpdateMap(String mobName, ManagedObjectReference mor, final Map<String, List<ManagedObjectReference>> morMap) {
        if (morMap.containsKey(mobName)) {
            morMap.get(mobName).add(mor);
        } else {
            ArrayList<ManagedObjectReference> list = new ArrayList<>(Collections.singletonList(mor));
            morMap.put(mobName, list);
        }
    }

    private PropertyFilterSpec createRecursiveFilterSpec(ManagedObjectReference container, String morefType,
                                                         String[] filterProps) throws Exception {
        try {
            ManagedObjectReference viewManager = serviceContent.getViewManager();
            ManagedObjectReference containerView = vimPort.createContainerView(viewManager, container,
                    Collections.singletonList(morefType), true);

            // Create property specification to specify list properties to
            // extract from given object type
            PropertySpec propSpec = new PropertySpec();
            propSpec.setAll(false);
            propSpec.getPathSet().addAll(Arrays.asList(filterProps));
            propSpec.setType(morefType);

            // Create traversal specification to specify inventory navigation
            // path
            TraversalSpec traversalSpc = new TraversalSpec();
            traversalSpc.setName("view");
            traversalSpc.setPath("view");
            traversalSpc.setSkip(false);
            traversalSpc.setType("ContainerView");

            // Create object specification to specify root location and
            // associate a traversal specification
            ObjectSpec objSpec = new ObjectSpec();
            objSpec.setObj(containerView);
            objSpec.setSkip(false);
            objSpec.getSelectSet().add(traversalSpc);

            // Create property filter specification, then set property spec and
            // object spec
            PropertyFilterSpec propertyFilterSpec = new PropertyFilterSpec();
            propertyFilterSpec.getPropSet().add(propSpec);
            propertyFilterSpec.getObjectSet().add(objSpec);

            return propertyFilterSpec;
        } catch (Exception exp) {
            System.out.println(String.format("##vso[task.logissue type=error;code=PREREQ_CreateFilterSpecFailed;TaskId=%s;]",
                    Constants.TASK_ID));
            throw new Exception("Failed to create filter spec: " + exp.getMessage());
        }
    }

    private VirtualMachineCloneSpec getVirtualMachineCloneSpec(String computeType, String computeName, String targetDS,
                                                               String customizationSpec, String description) throws Exception {

        VirtualMachineRelocateSpec relocSpec = getVirtualMachineRelocationSpec(computeType, computeName, targetDS);
        VirtualMachineConfigSpec configSpec = new VirtualMachineConfigSpec();
        configSpec.setAnnotation(description);
        VirtualMachineCloneSpec cloneSpec = new VirtualMachineCloneSpec();

        if ((customizationSpec != null) && !customizationSpec.isEmpty()) {
            System.out.println(String.format("Fetching customization specification with name [ %s ].", customizationSpec));
            CustomizationSpecItem customizationSpecItem = vimPort.getCustomizationSpec(
                    serviceContent.getCustomizationSpecManager(), customizationSpec);
            cloneSpec.setCustomization(customizationSpecItem.getSpec());
        }

        cloneSpec.setConfig(configSpec);
        cloneSpec.setLocation(relocSpec);
        cloneSpec.setPowerOn(true);
        cloneSpec.setTemplate(false);

        return cloneSpec;
    }

    private VirtualMachineRelocateSpec getVirtualMachineRelocationSpec(String computeType, String computeName, String targetDS) throws Exception {
        VirtualMachineRelocateSpec relocSpec = new VirtualMachineRelocateSpec();
        ManagedObjectReference targetCluster;
        ManagedObjectReference targetResourcePool;
        System.out.println(String.format("Searching for datastore with name [ %s ].", targetDS));
        ManagedObjectReference targetDSMor = getMorByName(targetDCMor, targetDS, DATA_STORE, false);
        switch (computeType) {
            case "ESXi Host":
                ManagedObjectReference targetHost = getMorByName(targetDCMor, computeName, HOST_SYSTEM, false);
                targetCluster = (ManagedObjectReference) getMorProperties(targetHost, new String[]{PARENT}).get(PARENT);
                targetResourcePool = (ManagedObjectReference) getMorProperties(targetCluster, new String[]{RESOURCE_POOL_PROP}).get(RESOURCE_POOL_PROP);
                relocSpec.setHost(targetHost);
                break;
            case "Cluster":
                targetCluster = getMorByName(targetDCMor, computeName, CLUSTER_COMPUTE_RESOURCE, false);
                targetResourcePool = (ManagedObjectReference) getMorProperties(targetCluster, new String[]{RESOURCE_POOL_PROP}).get(RESOURCE_POOL_PROP);
                break;
            case "Resource Pool":
                targetResourcePool = getMorByName(targetDCMor, computeName, RESOURCE_POOL, false);
                break;
            default:
                System.out.println(String.format("##vso[task.logissue type=error;code=INFRAISSUE_InvalidComputeType;TaskId=%s;]",
                        Constants.TASK_ID));
                throw new Exception("Invalid compute resource type: " + computeType);
        }
        relocSpec.setPool(targetResourcePool);
        relocSpec.setDatastore(targetDSMor);
        return relocSpec;
    }

    private boolean isSessionActive() {
        System.out.println("Checking for active session...");
        if (userSession == null) {
            return false;
        }
        long startTime = userSession.getLastActiveTime().toGregorianCalendar().getTime().getTime();
        return new Date().getTime() < startTime + 30 * 60 * 1000;
    }
}
