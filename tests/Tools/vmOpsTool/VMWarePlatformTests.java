import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class VMWarePlatformTests {
    private String vCenterUserName = "Administrator@vsphere.local";
    private String vCenterPassword = "Password~1";
    private String vCenterUrl = getvCenterUrl();
    private String defaultTargetDC = "fareastdc";
    private ConnectionData connData = new ConnectionData(vCenterUrl, vCenterUserName, vCenterPassword, defaultTargetDC, true);
    private IVMWare vmWareImpl = getVmWareImpl();
    private String snapshotOne = "Snapshot1";
    private String ubuntuTemplate = "Ubuntu";
    String linuxCustomizationSpec = "Linux Spec";
    private String snapshotTwo = "Snapshot2";

    public abstract IVMWare getVmWareImpl();

    public abstract String getvCenterUrl();

    // Common for all Operations
    // The following two scenarios needs to be validated during stress testing
    // restoreSnapshotShouldUseASessionTimeoutOfSixtyMinutes
    // restoreSnapshotShouldThrowIfWaitTimesOut
    // cloneVMFromTemplateWithInsufficientDiskSpaceShouldFail
    // cloneVMFromTemplateWithInsufficientComputeShouldFail

    // Clone VM from template action tests
    @Test
    public void cloneVMFromTemplateWithTargetComputeAsESXiHostShouldSucceed() throws Exception {
        String newVmName = "newVmOnEsxiHost";
        String targetDC = "redmonddc";
        String targetFolder = "";
        String computeType = "ESXi Host";
        String computeName = "idcvstt-lab318.corp.microsoft.com";
        String datastore = "datastore1";
        String description = "Creating new VM from ubuntuVM template on ESXi host";
        connData.setTargetDC(targetDC);

        vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);

        assertThat(vmWareImpl.isVMExists(newVmName, connData)).isEqualTo(true);

        vmWareImpl.deleteVM(newVmName, connData);

        assertThat(vmWareImpl.isVMExists(newVmName, connData)).isEqualTo(false);
    }

    @Test
    public void cloneVMFromTemplateWithTargetComputeAsClusterShouldSucceed() throws Exception {
        String newVmName = "newVmOnCluster";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "Cluster";
        String computeName = "fareastcluster";
        String datastore = "SharedStorage";
        String description = "Creating new VM from ubuntuVM template on cluster";
        connData.setTargetDC(targetDC);

        vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);

        assertThat(vmWareImpl.isVMExists(newVmName, connData)).isEqualTo(true);

        vmWareImpl.deleteVM(newVmName, connData);

        assertThat(vmWareImpl.isVMExists(newVmName, connData)).isEqualTo(false);
    }

    @Test
    public void cloneVMFromTemplateWithTargetComputeAsResourcePoolShouldSucceed() throws Exception {
        String newVmName = "newVmOnResourcePool";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "Resource Pool";
        String computeName = "fareastrp";
        String datastore = "SharedStorage";
        String description = "Creating new VM from ubuntuVM template on resource pool";
        connData.setTargetDC(targetDC);

        vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);

        assertThat(vmWareImpl.isVMExists(newVmName, connData)).isEqualTo(true);

        vmWareImpl.deleteVM(newVmName, connData);

        assertThat(vmWareImpl.isVMExists(newVmName, connData)).isEqualTo(false);
    }

    @Test
    public void cloneVMFromTemplateWithInvalidTargetComputeShouldFail() throws Exception {
        String newVmName = "newVM";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "Invalid Compute";
        String computeName = "fareastrp";
        String datastore = "datastore2";
        String description = "Creating new VM with invalid template";
        connData.setTargetDC(targetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromNonExistingTemplateShouldFail() throws Exception {
        String newVmName = "newVM";
        String nonExistingTemplate = "InvalidTemplate";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "Resource Pool";
        String computeName = "fareastrp";
        String datastore = "datastore2";
        String description = "Creating new VM with invalid template";
        connData.setTargetDC(targetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(nonExistingTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetDatacenterShouldFail() throws Exception {
        String newVmName = "newVM";
        String targetDC = "InvalidDc";
        String targetFolder = "";
        String computeType = "Resource Pool";
        String computeName = "fareastrp";
        String datastore = "datastore1";
        String description = "Creating new VM from ubuntuVM template on invalid data center";
        connData.setTargetDC(targetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetESXiShouldFail() {
        String newVmName = "newVM";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "ESXi Host";
        String computeName = "InvalidHost";
        String datastore = "datastore1";
        String description = "Creating new VM from ubuntuVM template on non existing ESXi host";
        connData.setTargetDC(targetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetCloudShouldFail() {
        String newVmName = "newVM";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "Cluster";
        String computeName = "InvalidCluster";
        String datastore = "datastore1";
        String description = "Creating new VM from ubuntuVM template on non existing cluster";
        connData.setTargetDC(targetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void cloneVMFromTemplateWithNonExistingTargetResourcePoolShouldFail() {
        String newVmName = "newVM";
        String targetDC = "fareastdc";
        String targetFolder = "";
        String computeType = "Resource Pool";
        String computeName = "InvalidRp";
        String datastore = "datastore1";
        String description = "Creating new VM from ubuntuVM template on non existing resource pool";
        connData.setTargetDC(targetDC);

        Exception exp = null;
        try {
            vmWareImpl.cloneVMFromTemplate(ubuntuTemplate, newVmName, targetFolder, computeType, computeName, datastore, linuxCustomizationSpec, description, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void connectShouldThrowForInvalidCredentials() {
        Exception exp = null;
        try {
            vmWareImpl.connect(new ConnectionData(vCenterUrl, vCenterUserName, "InvalidPassword", defaultTargetDC, true));
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    public void connectShouldThrowWithoutSkipCACheck() {
        Exception exp = null;
        try {
            vmWareImpl.connect(new ConnectionData(vCenterUrl, vCenterUserName, vCenterPassword, defaultTargetDC, false));
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void connectShouldNotThrowOnSuccessfulAuthentication() throws Exception {
        vmWareImpl.connect(connData);
    }

    @Test
    public void createSnapshotWithSaveVMMemoryShouldSucceed() throws Exception {
        String vmName = "Win8";
        String newSnapshot = "NewSnapshot";
        String targetDC = "fareastdc";
        connData.setTargetDC(targetDC);

        vmWareImpl.createSnapshot(vmName, newSnapshot, true, false, "Snapshot created during platform tests", 1200, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vmName, connData)).isEqualTo(newSnapshot);

        vmWareImpl.deleteSnapshot(vmName, newSnapshot, connData);
    }

    @Test
    public void createSnapshotWithQuiesceShouldSucceed() throws Exception {
        String vmName = "Win7";
        String newSnapshot = "NewSnapshot";
        String targetDC = "redmonddc";
        connData.setTargetDC(targetDC);

        vmWareImpl.createSnapshot(vmName, newSnapshot, false, true, "Snapshot created during platform tests", 1200, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vmName, connData)).isEqualTo(newSnapshot);

        vmWareImpl.deleteSnapshot(vmName, newSnapshot, connData);
    }

    @Test
    public void startAndStopOnAVmShouldNotThrow() throws Exception {
        String vmName = "startAndStopWindows";
        String targetDC = "redmonddc";
        connData.setTargetDC(targetDC);

        vmWareImpl.powerOnVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, true, connData)).isEqualTo(true);
        vmWareImpl.shutdownVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, false, connData)).isEqualTo(false);
    }

    @Test
    public void startAndStopVmShouldSucceedForLinuxVM() throws Exception {
        String vmName = "startAndStopUbuntu";
        String targetDC = "fareastdc";
        connData.setTargetDC(targetDC);

        vmWareImpl.powerOnVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, true, connData)).isEqualTo(true);
        vmWareImpl.powerOnVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, true, connData)).isEqualTo(true);

        vmWareImpl.shutdownVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, false, connData)).isEqualTo(false);
        vmWareImpl.shutdownVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, false, connData)).isEqualTo(false);
    }

    @Test
    public void powerOnAndPowerOffVmShouldSucceed() throws Exception {
        String vmName = "powerOnAndPowerOff";
        String targetDC = "fareastdc";
        connData.setTargetDC(targetDC);

        vmWareImpl.powerOnVM(vmName, 1200, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, true, connData)).isEqualTo(true);

        vmWareImpl.powerOffVM(vmName, connData);
        assertThat(vmWareImpl.isVMPoweredOn(vmName, false, connData)).isEqualTo(false);
    }

    // Common for restore/delete snapshot operations
    @Test
    public void restoreOrDeleteSnapshotShouldThrowIfSnapshotDoesNotExist() {
        Exception exp = null;
        String vmName = "Win2012R2";
        String targetDC = "redmonddc";
        connData.setTargetDC(targetDC);

        try {
            vmWareImpl.restoreSnapshot(vmName, "InvalidSnapshot", 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();

        exp = null;
        try {
            vmWareImpl.deleteSnapshot("Win2012R2", "InvalidSnapshot", connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    // Common for Snapshot operations
    @Test
    public void restoreSnapshotShouldThrowIfVmDoesNotExist() {
        Exception exp = null;
        String targetDC = "redmonddc";
        connData.setTargetDC(targetDC);

        try {
            vmWareImpl.restoreSnapshot("InvalidVM", snapshotOne, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void restoreOrCreateOrDeleteSnapshotShouldSucceedIfVmIsShutdown() throws Exception {
        String vmName = "PoweredOffVM";
        String newSnapshot = "NewSnapShot";
        String targetDC = "redmonddc";
        connData.setTargetDC(targetDC);

        vmWareImpl.createSnapshot(vmName, newSnapshot, false, false, "Snapshot created during platform tests", 1200, connData);
        assertThat(vmWareImpl.getCurrentSnapshot(vmName, connData)).isEqualTo(newSnapshot);

        vmWareImpl.restoreSnapshot(vmName, snapshotTwo, 1200, connData);
        assertThat(vmWareImpl.getCurrentSnapshot(vmName, connData)).isEqualTo(snapshotTwo);

        vmWareImpl.deleteSnapshot(vmName, newSnapshot, connData);
        assertThat(vmWareImpl.isSnapshotExists(vmName, newSnapshot, connData)).isEqualTo(false);
    }

    @Test
    public void restoreSnapshotShouldThrowIfMultipleVmsWithSameNameExistInADC() throws Exception {
        String vmName = "DuplicateVM";
        String targetDC = "fareastdc";
        connData.setTargetDC(targetDC);
        Exception exp = null;

        try {
            vmWareImpl.restoreSnapshot(vmName, snapshotOne, 1200, connData);
        } catch (Exception e) {
            exp = e;
        }
        assertThat(exp).isNotNull();
    }

    @Test
    public void restoreOrCreateOrDeleteSnapshotShouldSucceedForVMsEvenIfCaseDoesNotMatch() throws Exception {

        String vmName = "win2012r2";
        String newSnapshot = "NewSnapshot";
        String targetDC = "redmonddc";
        connData.setTargetDC(targetDC);

        vmWareImpl.createSnapshot(vmName, newSnapshot, false, false, "Snapshot created during platform tests", 1200, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vmName, connData)).isEqualTo(newSnapshot);

        vmWareImpl.restoreSnapshot(vmName, snapshotTwo, 1200, connData);

        assertThat(vmWareImpl.getCurrentSnapshot(vmName, connData)).isEqualTo(snapshotTwo);

        vmWareImpl.deleteSnapshot(vmName, newSnapshot, connData);

        assertThat(vmWareImpl.isSnapshotExists(vmName, newSnapshot, connData)).isEqualTo(false);
    }
}
