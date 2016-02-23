
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class VmOpsToolUnitTests {

    private InMemoryVMWareImpl vmWareImpl = new InMemoryVMWareImpl();
    private VmOpsTool vmOpsTool = new VmOpsTool(() -> vmWareImpl);
    private String vCenterUrl = "https://localhost:8080/sdk/vimservice";
    private String vCenterUserName = "Administrator";
    private String vCenterPassword = "Password~1";
    private String vCenterTargetDC = "DummyDC";
    private String vmSnapshotName = "Snapshot1";

    private ConnectionData connData = new ConnectionData(vCenterUrl, vCenterUserName, vCenterPassword, vCenterTargetDC, true);

    @Test
    public void parseCmdArgsWithAllRequiredInputs() {
        String[] cmdArgs = {Constants.VM_OPS_TOOL, Constants.V_CENTER_URL, vCenterUrl};

        Map<String, String> argsMap = VmOpsTool.parseCmdLine(cmdArgs);

        assertThat(argsMap.size()).isEqualTo(1);
        assertThat(argsMap.containsKey(Constants.VM_OPS_TOOL)).isEqualTo(false);
        assertThat(argsMap.containsKey(Constants.V_CENTER_URL)).isEqualTo(true);
        assertThat(argsMap.get(Constants.V_CENTER_URL)).isEqualTo(vCenterUrl);
    }

    @Test
    public void parseCmdArgsWithEmptyDescription() {
        String[] cmdArgs = {Constants.VM_OPS_TOOL, Constants.DESCRIPTION, ""};

        Map<String, String> argsMap = VmOpsTool.parseCmdLine(cmdArgs);

        assertThat(argsMap.size()).isEqualTo(1);
        assertThat(argsMap.containsKey(Constants.VM_OPS_TOOL)).isEqualTo(false);
        assertThat(argsMap.containsKey(Constants.DESCRIPTION)).isEqualTo(true);
        assertThat(argsMap.get(Constants.DESCRIPTION)).isEqualTo("");
    }

    @Test
    public void executeActionInParallelShouldSucceedForCloneAndDeleteVMActionWithValidInputs() throws Exception {
        String[] cmdArgs = getCmdArgs("newVM1, newVM2", Constants.CLONE_TEMPLATE, "dummyTemplate",
                Constants.COMPUTE_TYPE, "DummyCompute", Constants.COMPUTE_NAME, "DummyName", Constants.DESCRIPTION, "Dummy description");

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);

        assertThat(vmWareImpl.isVmExists("newVM1", connData)).isEqualTo(true);
        assertThat(vmWareImpl.isVmExists("newVM2", connData)).isEqualTo(true);

        // Delete vm validation
        cmdArgs = getCmdArgs("newVM1, newVM2", Constants.DELETE_VM, Constants.DELETE_VM_ACTION);

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        assertThat(vmWareImpl.isVmExists("newVM1", connData)).isEqualTo(false);
        assertThat(vmWareImpl.isVmExists("newVM2", connData)).isEqualTo(false);
    }

    @Test
    public void executeActionInParallelShouldSucceedForStartAndStopVMActionWithValidInputs() throws Exception {
        String[] cmdArgs = getCmdArgs("vm1, vm2", Constants.POWER_OPS, Constants.START_VM_ACTION);

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);

        assertThat(vmWareImpl.isVmPoweredOn("vm1", connData)).isEqualTo(true);
        assertThat(vmWareImpl.isVmPoweredOn("vm2", connData)).isEqualTo(true);

        cmdArgs = getCmdArgs("vm1, vm2", Constants.POWER_OPS, Constants.STOP_VM_ACTION);

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);

        assertThat(vmWareImpl.isVmPoweredOn("vm1", connData)).isEqualTo(false);
        assertThat(vmWareImpl.isVmPoweredOn("vm2", connData)).isEqualTo(false);
    }

    @Test
    public void executeActionInParallelShouldThrowForStartAndStopVMActionFailureOnAVM() throws Exception {
        String[] cmdArgs = getCmdArgs("vm1, VmThatFailsInStart", Constants.POWER_OPS, Constants.START_VM_ACTION);

        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.isVmPoweredOn("vm1", connData)).isEqualTo(true);

        cmdArgs = getCmdArgs("vm1, VmThatFailsInStop", Constants.POWER_OPS, Constants.STOP_VM_ACTION);

        exp = null;
        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.isVmPoweredOn("vm1", connData)).isEqualTo(false);
    }

    @Test
    public void executeActionInParallelInvalidPowerOperationShouldFail() {
        String[] cmdArgs = getCmdArgs("vm1, vm2", Constants.POWER_OPS, "pause");
        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void executeActionInParallelShouldThrowForCloneAndDeleteVMFailureOnAVM() throws Exception {
        String[] cmdArgs = getCmdArgs("newVM1, VMNameThatFailsInClone", Constants.CLONE_TEMPLATE, "dummyTemplate",
                Constants.COMPUTE_TYPE, "DummyCompute", Constants.COMPUTE_NAME, "DummyName", Constants.DESCRIPTION, "Dummy description");

        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.isVmExists("newVM1", connData)).isEqualTo(true);

        // Delete vm validation
        cmdArgs = getCmdArgs("newVM1, VMNameThatFailsInDelete", Constants.DELETE_VM, Constants.DELETE_VM_ACTION);
        exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.isVmExists("newVM1", connData)).isEqualTo(false);
    }

    @Test
    public void executeActionInParallelShouldSucceedForCreateAndDeleteSnapshotOperation() throws Exception {
        // Create snapshot operation validation
        String createSnapshot = "Sample Snapshot";
        String[] cmdArgs = getCmdArgs("vm1, vm2", Constants.SNAPSHOT_OPS, Constants.CREATE_SNAPSHOT_ACTION,
                Constants.SNAPSHOT_NAME, createSnapshot);

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);

        assertThat(vmWareImpl.getCurrentSnapshot("vm1", connData)).isEqualTo(createSnapshot);
        assertThat(vmWareImpl.getCurrentSnapshot("vm2", connData)).isEqualTo(createSnapshot);

        // Delete snapshot operation validation
        cmdArgs = getCmdArgs("vm1, vm2", Constants.SNAPSHOT_OPS, Constants.DELETE_SNAPSHOT_ACTION, Constants.SNAPSHOT_NAME,
                createSnapshot);

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);

        assertThat(vmWareImpl.isSnapshotExists("vm1", createSnapshot, connData)).isEqualTo(false);
        assertThat(vmWareImpl.isSnapshotExists("vm2", createSnapshot, connData)).isEqualTo(false);
    }

    @Test
    public void executeActionInParallelShouldRestoreSnapshotForRestoreOperation() throws Exception {

        String[] cmdArgs = getCmdArgs("vm1, vm2", Constants.SNAPSHOT_OPS, Constants.RESTORE_SNAPSHOT_ACTION,
                Constants.SNAPSHOT_NAME, vmSnapshotName);

        vmOpsTool.executeActionOnVmsInParallel(cmdArgs);

        assertThat(vmWareImpl.getCurrentSnapshot("vm1", connData)).isEqualTo(vmSnapshotName);
        assertThat(vmWareImpl.getCurrentSnapshot("vm2", connData)).isEqualTo(vmSnapshotName);
    }

    @Test
    public void executeActionInParallelShouldThrowForCreateAndDeleteSnapshotFailureOnAVM() throws Exception {
        // Delete snapshot operation throws on failure validation
        String vmSnapshot = "New Snapshot";
        String[] cmdArgs = getCmdArgs("vm1, vm3", Constants.SNAPSHOT_OPS, Constants.CREATE_SNAPSHOT_ACTION,
                Constants.SNAPSHOT_NAME, vmSnapshot);
        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.getCurrentSnapshot("vm1", connData)).isEqualTo(vmSnapshot);

        // Delete snapshot throws on failure validation
        exp = null;
        cmdArgs = getCmdArgs("vm1, vm3", Constants.SNAPSHOT_OPS, Constants.DELETE_SNAPSHOT_ACTION, Constants.SNAPSHOT_NAME,
                vmSnapshot);
        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.isSnapshotExists("vm1", vmSnapshot, connData)).isEqualTo(false);
    }

    @Test
    public void executeActionInParallelShouldThrowForRestoreSnapshotFailureOnAVM() throws Exception {
        String[] cmdArgs = getCmdArgs("vm1, vm3", Constants.SNAPSHOT_OPS, Constants.RESTORE_SNAPSHOT_ACTION,
                Constants.SNAPSHOT_NAME, vmSnapshotName);
        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
        assertThat(vmWareImpl.getCurrentSnapshot("vm1", connData)).isEqualTo(vmSnapshotName);
    }

    @Test
    public void executeActionInParallelInvalidSnapshotOperationShouldFail() {
        String[] cmdArgs = getCmdArgs("vm1, vm2", Constants.SNAPSHOT_OPS, "invalid", Constants.SNAPSHOT_NAME,
                vmSnapshotName);
        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void executeActionInParallelForInvalidActionNameShouldFail() {
        String[] cmdArgs = getCmdArgs("vm1, vm2", "-invalidOps", Constants.RESTORE_SNAPSHOT_ACTION,
                Constants.SNAPSHOT_NAME, vmSnapshotName);
        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void executeActionInParallelShouldThrowIfRequiredInputIsNotPresent() {
        String[] cmdArgs = new String[]{Constants.VM_OPS_TOOL};
        Exception exp = null;

        try {
            vmOpsTool.executeActionOnVmsInParallel(cmdArgs);
        } catch (Exception e) {
            exp = e;
        }

        assertThat(exp).isNotNull();
    }

    private String[] getCmdArgs(String vmList, String... vaArgs) {
        List<String> cmdArgs = new ArrayList<>();

        cmdArgs.add(Constants.VM_OPS_TOOL);
        cmdArgs.add(Constants.V_CENTER_URL);
        cmdArgs.add(vCenterUrl);
        cmdArgs.add(Constants.V_CENTER_USER_NAME);
        cmdArgs.add("dummyuser");
        cmdArgs.add(Constants.V_CENTER_PASSWORD);
        cmdArgs.add("dummypassword");
        cmdArgs.add(Constants.VM_LIST);
        cmdArgs.add(vmList);
        cmdArgs.add(Constants.TARGET_DC);
        cmdArgs.add("dummyDC");
        cmdArgs.add(Constants.SKIP_CA_CHECK);
        cmdArgs.add("true");

        Collections.addAll(cmdArgs, vaArgs);

        return cmdArgs.toArray(new String[cmdArgs.size()]);

    }
}
